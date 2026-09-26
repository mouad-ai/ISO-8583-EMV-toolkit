package io.github.mouadai.octet.core.input

/**
 * Finds hex dumps inside a line of log or console output: at least [MIN_BYTES] bytes written as
 * contiguous hex digits or as byte pairs separated by single spaces. Used by the console filter.
 */
object HexRuns {
    const val MIN_BYTES = 16

    private val RUN = Regex("(?<![0-9A-Fa-f])[0-9A-Fa-f]{2}(?: ?[0-9A-Fa-f]{2}){${MIN_BYTES - 1},}(?![0-9A-Fa-f])")

    /** Character ranges of each hex run in [line], in order. */
    fun find(line: String): List<IntRange> = RUN.findAll(line).map { it.range }.toList()
}
