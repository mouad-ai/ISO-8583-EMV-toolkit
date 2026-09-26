package io.github.mouadai.octet.core.iso

/**
 * Encodes and decodes one data element's length prefix and value according to its [FieldSpec].
 *
 * Length units: digits/characters for text types, bytes for [FieldType.B]. Length prefixes count the
 * same units. See DECISIONS.md (M2) for the conventions chosen where dialects differ.
 */
internal object FieldCodec {

    // ---- length prefixes -------------------------------------------------------------------

    fun prefixSize(lengthType: LengthType, encoding: LengthEncoding): Int = when (lengthType) {
        LengthType.FIXED -> 0
        else -> when (encoding) {
            LengthEncoding.ASCII, LengthEncoding.EBCDIC -> lengthType.digits
            LengthEncoding.BCD, LengthEncoding.BINARY -> Codecs.bcdByteCount(lengthType.digits)
        }
    }

    fun decodePrefix(bytes: ByteArray, lengthType: LengthType, encoding: LengthEncoding): Int = when (encoding) {
        LengthEncoding.ASCII, LengthEncoding.EBCDIC -> {
            val text = Codecs.text(bytes, encoding == LengthEncoding.EBCDIC)
            if (!Codecs.isDigits(text)) throw CodecException("${lengthType.name} length prefix '${printable(text)}' is not numeric")
            text.toInt()
        }
        LengthEncoding.BCD -> {
            val digits = Codecs.nibbles(bytes)
            if (!Codecs.isDigits(digits)) throw CodecException("${lengthType.name} length prefix ${Codecs.toHex(bytes)} is not valid BCD")
            digits.toInt()
        }
        LengthEncoding.BINARY -> bytes.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
    }

    fun encodePrefix(length: Int, lengthType: LengthType, encoding: LengthEncoding): ByteArray {
        val digits = lengthType.digits
        return when (encoding) {
            LengthEncoding.ASCII, LengthEncoding.EBCDIC -> {
                val text = length.toString().padStart(digits, '0')
                if (text.length > digits) throw CodecException("length $length does not fit a ${lengthType.name} prefix")
                Codecs.textBytes(text, encoding == LengthEncoding.EBCDIC)
            }
            LengthEncoding.BCD -> {
                val size = Codecs.bcdByteCount(digits)
                val text = length.toString().padStart(size * 2, '0')
                if (length.toString().length > digits) throw CodecException("length $length does not fit a ${lengthType.name} prefix")
                Codecs.packNibbles(text)
            }
            LengthEncoding.BINARY -> {
                val size = Codecs.bcdByteCount(digits)
                if (length >= 1 shl (8 * size)) throw CodecException("length $length does not fit a ${lengthType.name} prefix")
                ByteArray(size) { i -> (length shr (8 * (size - 1 - i))).toByte() }
            }
        }
    }

    // ---- values ----------------------------------------------------------------------------

    /** Number of bytes a value of [units] occupies. */
    fun byteCount(spec: FieldSpec, units: Int): Int {
        val enc = spec.dataEncoding
        return when {
            spec.type == FieldType.B -> if (enc.isCharacter) units * 2 else units
            enc.isCharacter || enc == DataEncoding.BINARY -> units
            spec.type == FieldType.XN -> if (units == 0) 0 else 1 + Codecs.bcdByteCount(units - 1)
            else -> Codecs.bcdByteCount(units)
        }
    }

    /** Decodes exactly `byteCount(spec, units)` bytes into the field's text form. */
    fun decodeValue(spec: FieldSpec, bytes: ByteArray, units: Int): String {
        val enc = spec.dataEncoding
        return when {
            spec.type == FieldType.B && enc.isCharacter -> {
                val text = Codecs.text(bytes, enc == DataEncoding.EBCDIC)
                if (!Codecs.isHex(text)) throw CodecException("binary value is not valid hex text")
                text
            }
            enc.isCharacter -> Codecs.text(bytes, enc == DataEncoding.EBCDIC)
            enc == DataEncoding.BINARY || spec.type == FieldType.B -> Codecs.toHex(bytes)
            spec.type == FieldType.XN && units > 0 ->
                Codecs.ascii(bytes.copyOfRange(0, 1)) + decodeBcd(spec, bytes.copyOfRange(1, bytes.size), units - 1)
            else -> decodeBcd(spec, bytes, units)
        }
    }

    private fun decodeBcd(spec: FieldSpec, bytes: ByteArray, digits: Int): String {
        var nibbles = Codecs.nibbles(bytes)
        if (nibbles.length > digits) {
            nibbles = if (padSide(spec) == PadSide.LEFT) nibbles.substring(1) else nibbles.substring(0, digits)
        }
        return if (spec.type == FieldType.Z) nibbles.replace('D', '=') else nibbles
    }

    /** Encodes [value] (the text form) and returns its bytes and length in units. */
    fun encodeValue(spec: FieldSpec, value: String): Pair<ByteArray, Int> {
        val padded = applyFixedPadding(spec, value)
        validate(spec, padded)
        val units = if (spec.type == FieldType.B) padded.length / 2 else padded.length
        if (spec.lengthType == LengthType.FIXED && units != spec.maxLength) {
            throw CodecException("length $units must be exactly ${spec.maxLength}")
        }
        if (units > spec.maxLength) throw CodecException("length $units exceeds maximum ${spec.maxLength}")
        val enc = spec.dataEncoding
        val bytes = when {
            spec.type == FieldType.B && enc.isCharacter -> Codecs.textBytes(padded, enc == DataEncoding.EBCDIC)
            enc.isCharacter -> Codecs.textBytes(padded, enc == DataEncoding.EBCDIC)
            enc == DataEncoding.BINARY || spec.type == FieldType.B -> Codecs.fromHex(padded)
            spec.type == FieldType.XN && padded.isNotEmpty() ->
                Codecs.asciiBytes(padded.substring(0, 1)) + encodeBcd(spec, padded.substring(1))
            else -> encodeBcd(spec, padded)
        }
        return bytes to units
    }

    private fun encodeBcd(spec: FieldSpec, digits: String): ByteArray {
        val nibbles = if (spec.type == FieldType.Z) digits.replace('=', 'D') else digits
        if (!Codecs.isHex(nibbles)) throw CodecException("value cannot be packed as BCD")
        val even = when {
            nibbles.length % 2 == 0 -> nibbles
            padSide(spec) == PadSide.LEFT -> bcdPadNibble(spec) + nibbles
            else -> nibbles + bcdPadNibble(spec)
        }
        return Codecs.packNibbles(even)
    }

    private fun padSide(spec: FieldSpec): PadSide =
        if (spec.dataEncoding == DataEncoding.BCD_RIGHT_PAD) PadSide.RIGHT else PadSide.LEFT

    private fun bcdPadNibble(spec: FieldSpec): Char =
        spec.padding?.char ?: if (padSide(spec) == PadSide.RIGHT) 'F' else '0'

    private fun applyFixedPadding(spec: FieldSpec, value: String): String {
        if (spec.lengthType != LengthType.FIXED) return value
        val target = if (spec.type == FieldType.B) spec.maxLength * 2 else spec.maxLength
        if (value.length >= target) return value
        // For BCD, `padding` names the odd-length pad nibble; short values still pad like plain digits.
        val padding = (if (spec.dataEncoding.isBcd) null else spec.padding)
            ?: defaultPadding(spec)
            ?: return value
        return if (padding.side == PadSide.LEFT) value.padStart(target, padding.char) else value.padEnd(target, padding.char)
    }

    private fun defaultPadding(spec: FieldSpec): Padding? = when (spec.type) {
        FieldType.N -> Padding(PadSide.LEFT, '0')
        FieldType.B, FieldType.XN -> null
        else -> Padding(PadSide.RIGHT, ' ')
    }

    private fun validate(spec: FieldSpec, value: String) {
        when (spec.type) {
            FieldType.N -> if (value.isNotEmpty() && !Codecs.isDigits(value)) throw CodecException("numeric value must contain digits only")
            FieldType.Z -> if (!value.all { it in '0'..'9' || it == '=' || it == 'D' }) {
                throw CodecException("track data may contain only digits and '='")
            }
            FieldType.XN -> if (value.isNotEmpty() &&
                (value[0] !in "CD" || (value.length > 1 && !Codecs.isDigits(value.substring(1))))
            ) {
                throw CodecException("value must be 'C' or 'D' followed by digits")
            }
            FieldType.B -> if (value.length % 2 != 0 || !Codecs.isHex(value)) throw CodecException("binary value must be an even number of hex digits")
            else -> {}
        }
    }

    private fun printable(s: String): String = s.map { if (it in ' '..'~') it else '.' }.joinToString("")
}
