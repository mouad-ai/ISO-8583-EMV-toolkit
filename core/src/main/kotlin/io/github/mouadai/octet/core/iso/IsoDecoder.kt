package io.github.mouadai.octet.core.iso

import java.util.TreeMap
import java.util.TreeSet

/**
 * Decodes EMV BER-TLV field content (field 55) into child elements. Offsets in the result must be
 * relative to the start of `data`; [IsoDecoder] relocates them into the message buffer.
 * The default is [EmvBerTlvSubfieldDecoder].
 */
fun interface BerTlvSubfieldDecoder {
    fun decode(data: ByteArray): SubfieldDecodeResult
}

/** Children of a field plus problems found while splitting it, at offsets relative to the field data. */
class SubfieldDecodeResult(val children: List<FieldValue>, val errors: List<SubfieldError> = emptyList())

data class SubfieldError(val offset: Int, val message: String)

/**
 * Best-effort ISO 8583 decoder (SPEC 6.7): decodes as far as the bytes allow and reports structured
 * errors with byte offsets. It never throws for any input bytes.
 */
class IsoDecoder(private val berTlvDecoder: BerTlvSubfieldDecoder? = EmvBerTlvSubfieldDecoder()) {

    /**
     * Decodes [bytes] with [dialect]. When [framing] is null, each of the dialect's framings is tried
     * and the first that decodes cleanly wins; if none does, the one that got furthest is returned.
     */
    fun decode(bytes: ByteArray, dialect: Dialect, framing: FramingSpec? = null): DecodeResult {
        if (framing != null) return decodeWith(bytes, dialect, framing)
        var best: DecodeResult? = null
        for (candidate in dialect.framings) {
            val result = decodeWith(bytes, dialect, candidate)
            if (result.isComplete) return result
            if (best == null || progress(result) > progress(best)) best = result
        }
        return best ?: decodeWith(bytes, dialect, FramingSpec.NONE)
    }

    private fun progress(result: DecodeResult): Int = result.errors.minOf { it.offset }

    private fun decodeWith(raw: ByteArray, dialect: Dialect, framing: FramingSpec): DecodeResult =
        Session(raw, dialect, framing).run()

    private inner class Session(val raw: ByteArray, val dialect: Dialect, val framing: FramingSpec) {
        var pos = 0

        /** End of the region being decoded: the buffer, or a field value while decoding its subfields. */
        var limit = raw.size
        val errors = ArrayList<DecodeError>()
        var frameLength: Int? = null
        var header: ByteArray? = null
        var mti: Mti? = null
        var bitmap: Bitmap? = null
        val fields = TreeMap<Int, FieldValue>()

        fun run(): DecodeResult {
            try {
                decodeAll()
            } catch (e: RuntimeException) {
                // Defensive: a bug must surface as a decode error, never as an exception to the UI.
                errors.add(DecodeError("Internal decoder error: ${e.message ?: e.javaClass.simpleName} at offset ${hexOffset(pos)}.", pos))
            }
            return DecodeResult(IsoMessage(dialect, framing, frameLength, header, mti, bitmap, fields, raw), errors)
        }

        private fun remaining() = limit - pos

        private fun fail(message: String, offset: Int = pos, fieldId: Int? = null): Boolean {
            errors.add(DecodeError("$message at offset ${hexOffset(offset)}.", offset, fieldId))
            return false
        }

        private fun take(n: Int): ByteArray = raw.copyOfRange(pos, pos + n).also { pos += n }

        private fun decodeAll() {
            if (!decodeFraming()) return
            if (!decodeMti()) return
            if (!decodeBitmaps()) return
            for (id in bitmap!!.present) {
                val spec = dialect.field(id)
                    ?: run { fail("Field $id is present in the bitmap but not defined in dialect '${dialect.name}'", pos, id); return }
                fields[id] = decodeElement(spec, id.toString(), id) ?: return
            }
            if (remaining() > 0) fail("${remaining()} unexpected trailing byte(s) after the last field")
        }

        private fun decodeFraming(): Boolean {
            when (framing.lengthPrefix) {
                LengthPrefix.NONE -> {}
                LengthPrefix.BINARY_2, LengthPrefix.ASCII_4 -> {
                    val size = if (framing.lengthPrefix == LengthPrefix.BINARY_2) 2 else 4
                    if (remaining() < size) return fail("Frame length prefix needs $size bytes but only ${remaining()} remain")
                    val start = pos
                    val prefix = take(size)
                    val declared = if (framing.lengthPrefix == LengthPrefix.BINARY_2) {
                        ((prefix[0].toInt() and 0xFF) shl 8) or (prefix[1].toInt() and 0xFF)
                    } else {
                        val text = Codecs.ascii(prefix)
                        if (!Codecs.isDigits(text)) return fail("Frame length prefix is not 4 ASCII digits", start)
                        text.toInt()
                    }
                    frameLength = declared
                    if (declared != remaining()) {
                        return fail("Frame length prefix says $declared bytes but ${remaining()} follow", start)
                    }
                }
            }
            if (framing.headerLength > 0) {
                if (remaining() < framing.headerLength) {
                    return fail("${framing.label} header needs ${framing.headerLength} bytes but only ${remaining()} remain")
                }
                header = take(framing.headerLength)
            }
            return true
        }

        private fun decodeMti(): Boolean {
            val size = HeaderCodec.mtiSize(dialect.mtiEncoding)
            if (remaining() < size) return fail("MTI needs $size bytes but only ${remaining()} remain")
            val start = pos
            val bytes = take(size)
            val code = try {
                HeaderCodec.decodeMti(bytes, dialect.mtiEncoding)
            } catch (e: CodecException) {
                return fail("${e.message}", start)
            }
            mti = Mti(code, start, size, bytes)
            return true
        }

        private fun decodeBitmaps(): Boolean {
            val spec = dialect.bitmap
            val size = HeaderCodec.bitmapSize(spec.encoding)
            val start = pos
            val bits = TreeSet<Int>()
            var base = 0
            while (true) {
                val which = when (base) { 0 -> "Primary"; 64 -> "Secondary"; else -> "Tertiary" }
                if (remaining() < size) return fail("$which bitmap needs $size bytes but only ${remaining()} remain")
                val at = pos
                val set = try {
                    HeaderCodec.decodeBitmap(take(size), spec.encoding, base)
                } catch (e: CodecException) {
                    return fail("$which ${e.message}", at)
                }
                bits.addAll(set)
                val next = base + 1
                if (base < 128 && next in set && (base == 0 || spec.tertiary)) base += 64 else break
            }
            bits.remove(1)
            if (spec.tertiary) bits.remove(65)
            bitmap = Bitmap(bits, start, pos - start, raw.copyOfRange(start, pos))
            return true
        }

        /**
         * Decodes one data element or bitmap-driven subfield at [pos]. Returns null after recording an
         * error when the element cannot be decoded; [errorField] is the top-level field to blame.
         */
        private fun decodeElement(spec: FieldSpec, elementId: String, errorField: Int): FieldValue? {
            val id = errorField
            val start = pos
            val units: Int
            val prefixSize = FieldCodec.prefixSize(spec.lengthType, spec.lengthEncoding)
            if (spec.lengthType == LengthType.FIXED) {
                units = spec.maxLength
            } else {
                if (remaining() < prefixSize) {
                    return failNull("${spec.label}: ${spec.lengthType.name} length prefix needs $prefixSize bytes but only ${remaining()} remain", start, id)
                }
                units = try {
                    FieldCodec.decodePrefix(take(prefixSize), spec.lengthType, spec.lengthEncoding)
                } catch (e: CodecException) {
                    return failNull("${spec.label}: ${e.message}", start, id)
                }
                if (units > spec.maxLength) {
                    return failNull("${spec.label}: ${spec.lengthType.name} length $units exceeds maximum ${spec.maxLength}", start, id)
                }
            }
            val size = FieldCodec.byteCount(spec, units)
            if (size > remaining()) {
                val what = if (spec.lengthType == LengthType.FIXED) "fixed length $units" else "${spec.lengthType.name} length $units"
                return failNull("${spec.label}: $what exceeds remaining ${remaining()} bytes", start, id)
            }
            val valueStart = pos
            val bytes = take(size)
            val value = try {
                FieldCodec.decodeValue(spec, bytes, units)
            } catch (e: CodecException) {
                return failNull("${spec.label}: ${e.message}", valueStart, id)
            }
            val children = spec.subfields?.let { decodeSubfields(spec, it, value, bytes, valueStart, errorField) } ?: emptyList()
            return FieldValue(
                id = elementId, name = spec.name, raw = raw.copyOfRange(start, pos), value = value,
                offset = start, length = pos - start, prefixLength = valueStart - start,
                sensitive = spec.sensitive, children = children,
            )
        }

        private fun failNull(message: String, offset: Int, fieldId: Int): FieldValue? {
            fail(message, offset, fieldId)
            return null
        }

        private fun decodeSubfields(
            spec: FieldSpec, layout: SubfieldLayout, value: String, bytes: ByteArray, valueStart: Int, errorField: Int,
        ): List<FieldValue> =
            when (layout) {
                is SubfieldLayout.Fixed -> decodeFixed(spec, layout, value, valueStart)
                is SubfieldLayout.PrivateTlv -> decodePrivateTlv(spec, layout, value, valueStart)
                SubfieldLayout.BerTlv -> decodeBerTlv(spec, bytes, valueStart)
                is SubfieldLayout.Bitmapped -> decodeBitmapped(spec, layout, valueStart, valueStart + bytes.size, errorField)
            }

        /** Decodes the value region [valueStart]..[valueEnd] as a bitmap plus the subfields it flags. */
        private fun decodeBitmapped(spec: FieldSpec, layout: SubfieldLayout.Bitmapped, valueStart: Int, valueEnd: Int, errorField: Int): List<FieldValue> {
            val savedPos = pos
            val savedLimit = limit
            pos = valueStart
            limit = valueEnd
            val out = ArrayList<FieldValue>()
            try {
                val size = if (layout.bitmapEncoding == BitmapEncoding.BINARY) layout.bitmapLength else layout.bitmapLength * 2
                if (remaining() < size) {
                    fail("${spec.label}: subfield bitmap needs $size bytes but only ${remaining()} remain", pos, errorField)
                    return out
                }
                val bitmapStart = pos
                val bitmapBytes = take(size)
                val bits = try {
                    HeaderCodec.decodeBitmap(bitmapBytes, layout.bitmapEncoding, 0)
                } catch (e: CodecException) {
                    fail("${spec.label}: subfield ${e.message}", bitmapStart, errorField)
                    return out
                }
                out.add(FieldValue("${spec.path ?: spec.id}.bitmap", "Bitmap", bitmapBytes, bits.joinToString(","), bitmapStart, size))
                for (bit in bits) {
                    val sub = layout.fields[bit]
                    if (sub == null) {
                        fail("${spec.label}: subfield $bit is present in the bitmap but not defined", pos, errorField)
                        return out
                    }
                    out.add(decodeElement(sub, sub.path ?: "${spec.id}.$bit", errorField) ?: return out)
                }
                if (remaining() > 0) fail("${spec.label}: ${remaining()} unexpected trailing byte(s) after the last subfield", pos, errorField)
                return out
            } finally {
                pos = savedPos
                limit = savedLimit
            }
        }

        /** Byte offset (relative to the value start) of character [index] of the decoded value. */
        private fun byteIndex(spec: FieldSpec, index: Int, valueLength: Int): Int {
            val enc = spec.dataEncoding
            return when {
                enc.isCharacter -> index
                enc == DataEncoding.BINARY || spec.type == FieldType.B -> index / 2
                spec.type == FieldType.XN -> if (index == 0) 0 else 1 + byteIndex(spec.copy(type = FieldType.N), index - 1, valueLength - 1)
                else -> {
                    val leftPad = if (valueLength % 2 == 1 && enc != DataEncoding.BCD_RIGHT_PAD) 1 else 0
                    (index + leftPad) / 2
                }
            }
        }

        private fun byteEnd(spec: FieldSpec, endIndex: Int, valueLength: Int): Int {
            if (endIndex == 0) return 0
            return byteIndex(spec, endIndex - 1, valueLength) + 1
        }

        private fun child(spec: FieldSpec, id: String, name: String, value: String, from: Int, to: Int, fullValue: String, valueStart: Int, sensitive: Boolean): FieldValue {
            val startByte = byteIndex(spec, from, fullValue.length)
            val endByte = byteEnd(spec, to, fullValue.length)
            return FieldValue(
                id = id, name = name, raw = raw.copyOfRange(valueStart + startByte, valueStart + endByte), value = value,
                offset = valueStart + startByte, length = endByte - startByte, sensitive = sensitive,
            )
        }

        private fun decodeFixed(spec: FieldSpec, layout: SubfieldLayout.Fixed, value: String, valueStart: Int): List<FieldValue> {
            val scale = if (spec.type == FieldType.B) 2 else 1
            val out = ArrayList<FieldValue>()
            var index = 0
            for (sub in layout.subfields) {
                if (index >= value.length) break
                val end = minOf(index + sub.length * scale, value.length)
                out.add(child(spec, "${spec.id}.${sub.id}", sub.name, value.substring(index, end), index, end, value, valueStart, sub.sensitive))
                index = end
            }
            return out
        }

        private fun decodePrivateTlv(spec: FieldSpec, layout: SubfieldLayout.PrivateTlv, value: String, valueStart: Int): List<FieldValue> {
            val out = ArrayList<FieldValue>()
            var index = 0
            while (index < value.length) {
                val headerEnd = index + layout.tagLength + layout.lengthLength
                val errorAt = valueStart + byteIndex(spec, index, value.length)
                if (headerEnd > value.length) {
                    fail("${spec.label}: TLV element needs ${layout.tagLength + layout.lengthLength} header characters but only ${value.length - index} remain", errorAt, spec.id)
                    break
                }
                val tag = value.substring(index, index + layout.tagLength)
                val lengthText = value.substring(index + layout.tagLength, headerEnd)
                if (!Codecs.isDigits(lengthText)) {
                    fail("${spec.label}: TLV tag '$tag' length '$lengthText' is not numeric", errorAt, spec.id)
                    break
                }
                val end = headerEnd + lengthText.toInt()
                if (end > value.length) {
                    fail("${spec.label}: TLV tag '$tag' length ${lengthText.toInt()} exceeds remaining ${value.length - headerEnd} characters", errorAt, spec.id)
                    break
                }
                val element = child(spec, "${spec.id}.$tag", layout.tagNames[tag] ?: "Tag $tag", value.substring(headerEnd, end), index, end, value, valueStart, false)
                out.add(
                    FieldValue(
                        element.id, element.name, element.raw, element.value, element.offset, element.length,
                        prefixLength = byteIndex(spec, headerEnd, value.length) - byteIndex(spec, index, value.length),
                    ),
                )
                index = end
            }
            return out
        }

        private fun decodeBerTlv(spec: FieldSpec, bytes: ByteArray, valueStart: Int): List<FieldValue> {
            val decoder = berTlvDecoder ?: return emptyList()
            val hexText = spec.type == FieldType.B && spec.dataEncoding.isCharacter
            val data = if (hexText) Codecs.fromHex(Codecs.text(bytes, spec.dataEncoding == DataEncoding.EBCDIC)) else bytes
            val scale = if (hexText) 2 else 1
            val result = try {
                decoder.decode(data)
            } catch (e: RuntimeException) {
                fail("${spec.label}: BER-TLV decoding failed (${e.message})", valueStart, spec.id)
                return emptyList()
            }
            for (error in result.errors) {
                // The TLV decoder words offsets relative to the field data; restate them in message terms.
                fail("${spec.label}: ${error.message.replace(TRAILING_OFFSET, "")}", valueStart + error.offset * scale, spec.id)
            }
            return result.children.map { it.relocated(valueStart, scale) }
        }
    }

    private companion object {
        val TRAILING_OFFSET = Regex("""\s*at offset 0x[0-9A-Fa-f]+\.?$""")
    }
}
