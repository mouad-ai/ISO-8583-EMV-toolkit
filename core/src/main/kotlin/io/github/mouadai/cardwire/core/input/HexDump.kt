package io.github.mouadai.cardwire.core.input

/**
 * Classic hex dump layout (`offset  hex bytes  ascii`) with a two-way mapping between byte offsets
 * and character positions in [text], so a UI can highlight a decoded element's bytes and find the
 * element under the caret. Bytes inside [masked] ranges (sensitive values) are shown as `**` / `*`.
 */
class HexDump(
    private val bytes: ByteArray,
    private val bytesPerLine: Int = 16,
    private val masked: List<IntRange> = emptyList(),
) {

    init {
        require(bytesPerLine > 0) { "bytesPerLine must be positive" }
    }

    private val offsetWidth = if (bytes.size > 0x10000) 8 else 4
    private val hexStart = offsetWidth + 2
    private val hexWidth = bytesPerLine * 3 - 1
    private val asciiStart = hexStart + hexWidth + 2

    /** Every line but the last has this many characters, followed by `\n`. */
    private val fullLineLength = asciiStart + bytesPerLine

    val text: String = buildString {
        for (lineStart in bytes.indices step bytesPerLine) {
            if (lineStart > 0) append('\n')
            val lineEnd = minOf(lineStart + bytesPerLine, bytes.size)
            append(lineStart.toString(16).uppercase().padStart(offsetWidth, '0'))
            append("  ")
            val hex = (lineStart until lineEnd).joinToString(" ") {
                if (isMasked(it)) "**" else "%02X".format(bytes[it].toInt() and 0xFF)
            }
            append(hex.padEnd(hexWidth))
            append("  ")
            for (i in lineStart until lineEnd) {
                val b = bytes[i].toInt() and 0xFF
                append(
                    when {
                        isMasked(i) -> '*'
                        b in 0x20..0x7E -> b.toChar()
                        else -> '.'
                    },
                )
            }
        }
    }

    private fun isMasked(index: Int): Boolean = masked.any { index in it }

    /**
     * Inclusive character ranges in [text] covering bytes `[offset, offset + length)`: for each line
     * touched, one range in the hex column and one in the ASCII column. Out-of-range bytes are ignored.
     */
    fun highlightRanges(offset: Int, length: Int): List<IntRange> {
        val first = maxOf(offset, 0)
        val last = minOf(offset.toLong() + length, bytes.size.toLong()).toInt() - 1
        if (first > last) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var start = first
        while (start <= last) {
            val line = start / bytesPerLine
            val end = minOf(last, (line + 1) * bytesPerLine - 1)
            val base = line * (fullLineLength + 1)
            val startColumn = start % bytesPerLine
            val endColumn = end % bytesPerLine
            ranges += base + hexStart + startColumn * 3..base + hexStart + endColumn * 3 + 1
            ranges += base + asciiStart + startColumn..base + asciiStart + endColumn
            start = end + 1
        }
        return ranges
    }

    /** Byte offset shown at character position [textOffset], or null for offsets, separators and padding. */
    fun byteOffsetAt(textOffset: Int): Int? {
        if (textOffset < 0 || textOffset >= text.length) return null
        val line = textOffset / (fullLineLength + 1)
        val column = textOffset % (fullLineLength + 1)
        val indexInLine = when {
            column in hexStart until hexStart + hexWidth && (column - hexStart) % 3 != 2 -> (column - hexStart) / 3
            column in asciiStart until fullLineLength -> column - asciiStart
            else -> return null
        }
        return (line * bytesPerLine + indexInLine).takeIf { it < bytes.size }
    }
}
