package io.github.mouadai.cardwire.core.emv

/** Masking of sensitive values for display and copy (SPEC 6.8). */
object Masking {
    const val MASK_CHAR = '*'

    /** Keeps the first 6 and last 4 digits; shorter values are fully masked. */
    fun pan(pan: String): String =
        if (pan.length < 11) full(pan) else pan.take(6) + MASK_CHAR.toString().repeat(pan.length - 10) + pan.takeLast(4)

    /** Track 2: PAN rule up to the separator, everything after it hidden. */
    fun track2(track: String): String {
        val sep = track.indexOfFirst { it == 'D' || it == 'd' || it == '=' }
        if (sep < 0) return full(track)
        return pan(track.substring(0, sep)) + track[sep] + full(track.substring(sep + 1))
    }

    fun full(value: String): String = MASK_CHAR.toString().repeat(value.length)

    fun apply(rule: MaskRule, value: String): String = when (rule) {
        MaskRule.PAN -> pan(value)
        MaskRule.TRACK2 -> track2(value)
        MaskRule.FULL -> full(value)
    }
}
