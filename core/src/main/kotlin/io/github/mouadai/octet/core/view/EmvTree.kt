package io.github.mouadai.octet.core.view

import io.github.mouadai.octet.core.emv.EmvDecodeResult
import io.github.mouadai.octet.core.emv.EmvElement
import io.github.mouadai.octet.core.emv.EmvTagDictionary

/** Adapts an M1 [EmvDecodeResult] into the display tree shown by the decode tool window. */
object EmvTree {

    fun build(result: EmvDecodeResult, inputSize: Int): DecodeNode = DecodeNode(
        id = "EMV TLV",
        name = "${result.elements.size} tags",
        value = "",
        offset = 0,
        length = inputSize,
        children = result.elements.map(::element),
    )

    /** Byte ranges holding sensitive values that are currently masked, for the hex view. */
    fun maskedRanges(result: EmvDecodeResult): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        fun visit(e: EmvElement) {
            if (e.masked && e.length > 0) ranges += e.node.valueOffset until e.node.valueOffset + e.length
            e.children.forEach(::visit)
        }
        result.elements.forEach(::visit)
        return ranges
    }

    private fun element(e: EmvElement): DecodeNode {
        val valueOffset = e.node.valueOffset
        val bits = e.bits.map {
            DecodeNode(
                id = it.toString().substringBefore(':'),
                name = it.meaning,
                value = "",
                offset = valueOffset + it.byte - 1,
                length = 1,
            )
        }
        val dol = e.dol.map {
            DecodeNode(
                id = it.tag.hex,
                name = EmvTagDictionary.default[it.tag]?.name ?: "Unknown tag",
                value = "length ${it.length}",
                offset = it.offset,
                length = it.tag.size + 1,
            )
        }
        // Warnings have no bytes of their own; zero length keeps them out of hex-click lookups.
        val warnings = e.warnings.map { DecodeNode(id = "!", name = it, value = "", offset = e.offset, length = 0) }
        return DecodeNode(
            id = e.tag,
            name = e.name,
            value = e.value,
            rawHex = e.rawHex,
            offset = e.offset,
            length = e.node.totalLength,
            children = warnings + bits + dol + e.children.map(::element),
        )
    }
}
