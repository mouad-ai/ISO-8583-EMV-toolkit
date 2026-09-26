package io.github.mouadai.cardwire.core.emv

/** One entry of a Data Object List: a tag and the length the card expects for it. No value. */
data class DolEntry(val tag: TlvTag, val length: Int, val offset: Int)

data class DolParseResult(val entries: List<DolEntry>, val errors: List<TlvError>)

/** Parser for DOLs such as PDOL (9F38), CDOL1/2 (8C/8D), DDOL (9F49) and TDOL (97). */
object Dol {
    fun parse(hex: String): DolParseResult = parse(Hex.decode(hex))

    fun parse(data: ByteArray, baseOffset: Int = 0): DolParseResult {
        val entries = mutableListOf<DolEntry>()
        val errors = mutableListOf<TlvError>()
        var pos = 0
        while (pos < data.size) {
            val start = pos
            pos++
            if (data[start].toInt() and 0x1F == 0x1F) {
                while (pos < data.size && data[pos].toInt() and 0x80 != 0) pos++
                pos++
            }
            if (pos > data.size) {
                errors += TlvError(baseOffset + start, "DOL tag at offset ${Hex.offset(baseOffset + start)} is truncated.")
                break
            }
            val tag = TlvTag(data.copyOfRange(start, pos))
            if (pos >= data.size) {
                errors += TlvError(baseOffset + pos, "DOL tag ${tag.hex}: missing length at offset ${Hex.offset(baseOffset + pos)}.")
                break
            }
            // DOL lengths are a single byte (EMV Book 3, 5.4).
            entries += DolEntry(tag, data[pos].toInt() and 0xFF, baseOffset + start)
            pos++
        }
        return DolParseResult(entries, errors)
    }
}
