package io.github.mouadai.octet.core.iso

import java.util.TreeSet

/** MTI and bitmap conversions (SPEC 6.2, 6.3). */
internal object HeaderCodec {

    fun mtiSize(encoding: MtiEncoding): Int = if (encoding == MtiEncoding.BCD) 2 else 4

    fun decodeMti(bytes: ByteArray, encoding: MtiEncoding): String {
        val code = when (encoding) {
            MtiEncoding.ASCII -> Codecs.ascii(bytes)
            MtiEncoding.EBCDIC -> Codecs.ebcdic(bytes)
            MtiEncoding.BCD -> Codecs.nibbles(bytes)
        }
        if (code.length != 4 || !Codecs.isDigits(code)) throw CodecException("MTI ${Codecs.toHex(bytes)} is not 4 digits")
        return code
    }

    fun encodeMti(code: String, encoding: MtiEncoding): ByteArray {
        if (code.length != 4 || !Codecs.isDigits(code)) throw CodecException("MTI '$code' must be 4 digits")
        return when (encoding) {
            MtiEncoding.ASCII -> Codecs.asciiBytes(code)
            MtiEncoding.EBCDIC -> Codecs.ebcdicBytes(code)
            MtiEncoding.BCD -> Codecs.packNibbles(code)
        }
    }

    /** Bytes one 64-bit bitmap occupies. */
    fun bitmapSize(encoding: BitmapEncoding): Int = if (encoding == BitmapEncoding.BINARY) 8 else 16

    /** Returns the 1-based bit numbers set in one 64-bit bitmap, offset by [base] (0, 64 or 128). */
    fun decodeBitmap(bytes: ByteArray, encoding: BitmapEncoding, base: Int): List<Int> {
        val binary = when (encoding) {
            BitmapEncoding.BINARY -> bytes
            BitmapEncoding.HEX_ASCII, BitmapEncoding.HEX_EBCDIC -> {
                val text = Codecs.text(bytes, encoding == BitmapEncoding.HEX_EBCDIC)
                if (!Codecs.isHex(text)) throw CodecException("bitmap '${text.map { if (it in ' '..'~') it else '.' }.joinToString("")}' is not hexadecimal")
                Codecs.fromHex(text)
            }
        }
        val bits = ArrayList<Int>()
        for (i in 0 until 64) {
            if ((binary[i / 8].toInt() shr (7 - i % 8)) and 1 == 1) bits.add(base + i + 1)
        }
        return bits
    }

    fun encodeBitmap(bits: Collection<Int>, encoding: BitmapEncoding, base: Int): ByteArray {
        val binary = ByteArray(8)
        for (bit in bits) {
            val i = bit - base - 1
            if (i in 0 until 64) binary[i / 8] = (binary[i / 8].toInt() or (0x80 ushr (i % 8))).toByte()
        }
        return when (encoding) {
            BitmapEncoding.BINARY -> binary
            BitmapEncoding.HEX_ASCII -> Codecs.asciiBytes(Codecs.toHex(binary))
            BitmapEncoding.HEX_EBCDIC -> Codecs.ebcdicBytes(Codecs.toHex(binary))
        }
    }

    /** All bitmaps for a set of data element numbers, indicator bits included. */
    fun encodeBitmaps(fields: Set<Int>, spec: BitmapSpec): ByteArray {
        val maxField = fields.maxOrNull() ?: 0
        val tertiary = maxField > 128
        if (tertiary && !spec.tertiary) throw CodecException("fields above 128 need a dialect with a tertiary bitmap")
        val secondary = maxField > 64
        val bits = TreeSet(fields)
        if (secondary) bits.add(1)
        if (tertiary) bits.add(65)
        var out = encodeBitmap(bits, spec.encoding, 0)
        if (secondary) out += encodeBitmap(bits, spec.encoding, 64)
        if (tertiary) out += encodeBitmap(bits, spec.encoding, 128)
        return out
    }
}
