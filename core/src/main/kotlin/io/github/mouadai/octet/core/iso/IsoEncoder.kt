package io.github.mouadai.octet.core.iso

import java.io.ByteArrayOutputStream

/**
 * Builds ISO 8583 bytes from [IsoMessageData] with a [Dialect]. Values use the same text form that
 * [IsoDecoder] produces, so `encode(decode(x).message.toData())` reproduces `x`. Never throws:
 * invalid input yields [EncodeResult.Failure] listing every problem found.
 */
object IsoEncoder {

    fun encode(data: IsoMessageData, dialect: Dialect, framing: FramingSpec = FramingSpec.NONE): EncodeResult {
        val errors = ArrayList<EncodeError>()
        val body = ByteArrayOutputStream()

        try {
            body.write(HeaderCodec.encodeMti(data.mti, dialect.mtiEncoding))
        } catch (e: CodecException) {
            errors.add(EncodeError("${e.message}."))
        }

        val ids = data.fields.keys
        ids.filter { it < 2 || it > 192 || (it == 65 && dialect.bitmap.tertiary) }.forEach {
            errors.add(EncodeError("Field $it cannot carry data; it is outside 2-192 or a bitmap indicator.", it))
        }
        try {
            body.write(HeaderCodec.encodeBitmaps(ids.filter { it in 2..192 }.toSet(), dialect.bitmap))
        } catch (e: CodecException) {
            errors.add(EncodeError("Bitmap: ${e.message}."))
        }

        for ((id, value) in data.fields.toSortedMap()) {
            if (id < 2 || id > 192) continue
            val spec = dialect.field(id)
            if (spec == null) {
                errors.add(EncodeError("Field $id is not defined in dialect '${dialect.name}'.", id))
                continue
            }
            try {
                val (bytes, units) = FieldCodec.encodeValue(spec, value)
                if (spec.lengthType != LengthType.FIXED) {
                    body.write(FieldCodec.encodePrefix(units, spec.lengthType, spec.lengthEncoding))
                }
                body.write(bytes)
            } catch (e: CodecException) {
                errors.add(EncodeError("${spec.label}: ${e.message}.", id))
            }
        }

        val header = data.header ?: ByteArray(0)
        if (header.size != framing.headerLength) {
            errors.add(EncodeError("Framing '${framing.label}' needs a ${framing.headerLength}-byte header but ${header.size} bytes were given."))
        }
        if (errors.isNotEmpty()) return EncodeResult.Failure(errors)

        val frame = header + body.toByteArray()
        val prefix = when (framing.lengthPrefix) {
            LengthPrefix.NONE -> ByteArray(0)
            LengthPrefix.BINARY_2 -> {
                if (frame.size > 0xFFFF) return EncodeResult.Failure(listOf(EncodeError("Message of ${frame.size} bytes is too long for a 2-byte length prefix.")))
                byteArrayOf((frame.size shr 8).toByte(), frame.size.toByte())
            }
            LengthPrefix.ASCII_4 -> {
                if (frame.size > 9999) return EncodeResult.Failure(listOf(EncodeError("Message of ${frame.size} bytes is too long for a 4-digit length prefix.")))
                Codecs.asciiBytes(frame.size.toString().padStart(4, '0'))
            }
        }
        return EncodeResult.Success(prefix + frame)
    }
}
