package io.github.mouadai.cardwire.core.emv

/** Hex conversion helpers shared by the EMV code. */
object Hex {
    private val DIGITS = "0123456789ABCDEF".toCharArray()

    fun encode(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): String {
        val out = StringBuilder((to - from) * 2)
        for (i in from until to) {
            val b = bytes[i].toInt() and 0xFF
            out.append(DIGITS[b ushr 4]).append(DIGITS[b and 0x0F])
        }
        return out.toString()
    }

    /**
     * Parses hex as pasted from logs: whitespace, `0x` prefixes and `:`/`-` separators are ignored,
     * case does not matter. Throws [IllegalArgumentException] on anything else or an odd digit count.
     */
    fun decode(text: String): ByteArray {
        val clean = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '0' && i + 1 < text.length && (text[i + 1] == 'x' || text[i + 1] == 'X') -> i++
                c.isWhitespace() || c == ':' || c == '-' -> Unit
                Character.digit(c, 16) >= 0 -> clean.append(c)
                else -> throw IllegalArgumentException("Invalid hex character '$c' at position $i")
            }
            i++
        }
        require(clean.length % 2 == 0) { "Odd number of hex digits (${clean.length})" }
        return ByteArray(clean.length / 2) { idx ->
            ((Character.digit(clean[idx * 2], 16) shl 4) or Character.digit(clean[idx * 2 + 1], 16)).toByte()
        }
    }

    fun offset(value: Int): String = "0x%X".format(value)
}
