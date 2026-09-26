package io.github.mouadai.cardwire.core.iso

import java.nio.charset.Charset

/** Internal signal for malformed bytes or values; always converted to a [DecodeError]/[EncodeError]. */
internal class CodecException(message: String) : RuntimeException(message)

/** Low-level byte conversions shared by the MTI, bitmap, length-prefix and field codecs. */
internal object Codecs {
    /** ISO-8859-1 maps every byte to one char and back, so "ASCII" fields round-trip any byte. */
    private val ASCII: Charset = Charsets.ISO_8859_1

    /** IBM037 (EBCDIC US/Canada) decodes all 256 bytes to distinct chars, so it round-trips too. */
    private val EBCDIC: Charset = Charset.forName("IBM037")

    private const val HEX = "0123456789ABCDEF"

    fun ascii(bytes: ByteArray): String = String(bytes, ASCII)
    fun ebcdic(bytes: ByteArray): String = String(bytes, EBCDIC)

    fun asciiBytes(s: String): ByteArray {
        if (s.any { it.code > 0xFF }) throw CodecException("contains characters outside ISO-8859-1")
        return s.toByteArray(ASCII)
    }

    fun ebcdicBytes(s: String): ByteArray {
        val bytes = s.toByteArray(EBCDIC)
        if (ebcdic(bytes) != s) throw CodecException("contains characters that have no EBCDIC encoding")
        return bytes
    }

    fun text(bytes: ByteArray, ebcdic: Boolean): String = if (ebcdic) ebcdic(bytes) else ascii(bytes)
    fun textBytes(s: String, ebcdic: Boolean): ByteArray = if (ebcdic) ebcdicBytes(s) else asciiBytes(s)

    fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    fun isHex(s: String): Boolean = s.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }

    fun fromHex(s: String): ByteArray {
        if (s.length % 2 != 0) throw CodecException("hex value has an odd number of digits")
        if (!isHex(s)) throw CodecException("value is not hexadecimal")
        return ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /** Nibbles of [bytes] as uppercase hex characters. */
    fun nibbles(bytes: ByteArray): String = toHex(bytes)

    /** Packs hex-digit characters two per byte; [digits] must have an even length. */
    fun packNibbles(digits: String): ByteArray = fromHex(digits)

    fun isDigits(s: String): Boolean = s.isNotEmpty() && s.all { it in '0'..'9' }

    fun bcdByteCount(digits: Int): Int = (digits + 1) / 2
}
