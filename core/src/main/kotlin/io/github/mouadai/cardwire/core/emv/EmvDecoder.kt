package io.github.mouadai.cardwire.core.emv

/** A TLV node together with its dictionary entry and its human-readable interpretation. */
data class EmvElement(
    val node: TlvNode,
    val info: TagInfo?,
    /** Display value; masked when the tag is sensitive and masking is on. */
    val value: String,
    /** Raw value as hex; masked the same way as [value] when needed. */
    val rawHex: String,
    val masked: Boolean,
    /** Set bits and coded sub-values for bitfield tags. */
    val bits: List<BitMeaning>,
    /** Entries for DOL tags (PDOL, CDOL1/2, DDOL, TDOL). */
    val dol: List<DolEntry>,
    val children: List<EmvElement>,
    val warnings: List<String>,
) {
    val tag: String get() = node.tag.hex
    val name: String get() = info?.name ?: "Unknown tag"
    val offset: Int get() = node.offset
    val length: Int get() = node.length
}

data class EmvDecodeResult(val elements: List<EmvElement>, val errors: List<TlvError>) {
    fun find(tag: String): EmvElement? {
        fun search(list: List<EmvElement>): EmvElement? =
            list.firstNotNullOfOrNull { if (it.tag.equals(tag, ignoreCase = true)) it else search(it.children) }
        return search(elements)
    }

    /** Plain-text report, one line per tag, indented for nesting. */
    fun toText(): String = buildString {
        fun render(e: EmvElement, depth: Int) {
            val indent = "  ".repeat(depth)
            append(indent).append(e.tag).append(' ').append(e.name)
                .append(" [").append(e.length).append("]: ").append(e.value).append('\n')
            e.bits.forEach { append(indent).append("    ").append(it).append('\n') }
            e.dol.forEach { d ->
                append(indent).append("    ").append(d.tag.hex).append(" (")
                    .append(EmvTagDictionary.default[d.tag]?.name ?: "Unknown tag").append(") length ").append(d.length).append('\n')
            }
            e.warnings.forEach { append(indent).append("    ! ").append(it).append('\n') }
            e.children.forEach { render(it, depth + 1) }
        }
        elements.forEach { render(it, 0) }
        errors.forEach { append("Error: ").append(it.message).append('\n') }
    }
}

/**
 * Decodes EMV BER-TLV data (field 55 or standalone) into named, formatted elements.
 * Pure and offline; never throws on malformed input.
 */
class EmvDecoder(
    private val dictionary: EmvTagDictionary = EmvTagDictionary.default,
    private val bitfields: EmvBitfields = EmvBitfields.default,
    private val formatter: EmvFormatter = EmvFormatter(),
) {
    fun decode(hex: String, reveal: Boolean = false): EmvDecodeResult {
        val bytes = try {
            Hex.decode(hex)
        } catch (e: IllegalArgumentException) {
            return EmvDecodeResult(emptyList(), listOf(TlvError(0, e.message ?: "Invalid hex")))
        }
        return decode(bytes, reveal = reveal)
    }

    /** @param reveal show sensitive values in clear (the UI's per-session toggle). */
    fun decode(data: ByteArray, baseOffset: Int = 0, reveal: Boolean = false): EmvDecodeResult =
        interpret(BerTlv.decode(data, baseOffset), reveal)

    fun interpret(parsed: TlvParseResult, reveal: Boolean = false): EmvDecodeResult {
        val context = AmountContext(
            currencyCode = parsed.find("5F2A")?.valueHex?.takeLast(3),
            exponent = parsed.find("5F36")?.valueHex?.toIntOrNull(),
        )
        return EmvDecodeResult(parsed.nodes.map { element(it, context, reveal) }, parsed.errors)
    }

    private fun element(node: TlvNode, context: AmountContext, reveal: Boolean): EmvElement {
        val info = dictionary[node.tag]
        val kind = info?.display ?: DisplayKind.HEX
        val value = node.value
        val warnings = mutableListOf<String>()
        if (node.isTruncated) warnings += "Value truncated: ${node.length} of ${node.declaredLength} bytes present"

        val formatted = formatter.format(kind, value, context)
        formatted.warning?.let { warnings += it }

        val bits = if (kind in BIT_KINDS && bitfields.supports(node.tag.hex)) bitfields.decode(node.tag.hex, value) else emptyList()
        val dol = if (kind == DisplayKind.DOL) {
            Dol.parse(value, node.valueOffset).also { r -> r.errors.forEach { warnings += it.message } }.entries
        } else emptyList()

        val mask = info?.mask?.takeUnless { reveal }
        val shown = mask?.let { Masking.apply(it, formatted.text) } ?: formatted.text
        val rawHex = when (mask) {
            null -> node.valueHex
            MaskRule.FULL -> Masking.full(node.valueHex)
            else -> Masking.apply(mask, node.valueHex)
        }

        return EmvElement(
            node = node,
            info = info,
            value = shown,
            rawHex = rawHex,
            masked = mask != null,
            bits = bits,
            dol = dol,
            children = node.children.map { element(it, context, reveal) },
            warnings = warnings,
        )
    }

    private companion object {
        val BIT_KINDS = setOf(DisplayKind.BITFIELD, DisplayKind.CVM, DisplayKind.CID)
    }
}
