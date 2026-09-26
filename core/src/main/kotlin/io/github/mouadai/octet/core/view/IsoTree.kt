package io.github.mouadai.octet.core.view

import io.github.mouadai.octet.core.emv.EmvDecoder
import io.github.mouadai.octet.core.emv.Hex
import io.github.mouadai.octet.core.emv.Masking
import io.github.mouadai.octet.core.iso.DecodeResult
import io.github.mouadai.octet.core.iso.FieldValue
import io.github.mouadai.octet.core.iso.IsoMessage
import io.github.mouadai.octet.core.iso.SubfieldLayout

/**
 * Adapts an M2 [DecodeResult] into the display tree: framing, MTI, bitmap and data elements.
 * Sensitive fields are masked unless [reveal] is set, and field 55 (BER-TLV) is re-read with the
 * M1 [EmvDecoder] so its tags show formatted values and bit meanings.
 */
class IsoTree(private val emvDecoder: EmvDecoder = EmvDecoder()) {

    class Result(val root: DecodeNode, val maskedRanges: List<IntRange>)

    fun build(result: DecodeResult, reveal: Boolean): Result {
        val message = result.message
        val masked = mutableListOf<IntRange>()
        val children = buildList {
            framing(message)?.let(::add)
            message.mti?.let {
                add(DecodeNode("MTI", it.description, it.code, offset = it.offset, length = it.length, rawHex = Hex.encode(it.raw)))
            }
            message.bitmap?.let {
                add(
                    DecodeNode(
                        "Bitmap", "Fields present", it.dataFields.joinToString(", "),
                        offset = it.offset, length = it.length, rawHex = Hex.encode(it.raw),
                    ),
                )
            }
            message.fields.forEach { (id, field) -> add(field(message, id, field, reveal, masked)) }
        }
        val root = DecodeNode(
            id = "ISO 8583",
            name = message.dialect.name,
            value = message.mti?.code.orEmpty(),
            offset = 0,
            length = message.raw.size,
            children = children,
        )
        return Result(root, masked)
    }

    private fun framing(message: IsoMessage): DecodeNode? {
        val start = message.mti?.offset ?: return null
        if (start == 0) return null
        val parts = listOfNotNull(
            message.frameLength?.let { "length $it" },
            message.header?.let { "header ${Hex.encode(it)}" },
        )
        return DecodeNode(
            "Framing", message.framing.label, parts.joinToString(", "),
            offset = 0, length = start, rawHex = Hex.encode(message.raw.copyOfRange(0, start)),
        )
    }

    private fun field(message: IsoMessage, id: Int, field: FieldValue, reveal: Boolean, masked: MutableList<IntRange>): DecodeNode {
        val spec = message.dialect.field(id)
        val node = element("DE $id", field, reveal, masked, maskRule(id))
        if (spec?.subfields != SubfieldLayout.BerTlv) return node
        val emv = emvChildren(field, reveal, masked) ?: return node
        return node.copy(children = emv)
    }

    private fun element(
        id: String,
        field: FieldValue,
        reveal: Boolean,
        masked: MutableList<IntRange>,
        rule: (String) -> String = Masking::full,
    ): DecodeNode {
        val hide = field.sensitive && !reveal
        if (hide && field.valueLength > 0) masked += field.valueOffset until field.valueOffset + field.valueLength
        val rawHex = Hex.encode(field.raw)
        return DecodeNode(
            id = id,
            name = field.name,
            value = if (hide) rule(field.value) else field.value,
            rawHex = if (hide) Masking.full(rawHex) else rawHex,
            offset = field.offset,
            length = field.length,
            children = field.children.map { element(it.id, it, reveal, masked) },
        )
    }

    /**
     * Field 55 as formatted EMV tags. The field value is the TLV as hex; in hex-text dialects each
     * byte takes two characters in the message, so offsets are scaled by the ratio of message bytes
     * to TLV bytes. Returns null when the value is not clean hex, leaving the M2 children in place.
     */
    private fun emvChildren(field: FieldValue, reveal: Boolean, masked: MutableList<IntRange>): List<DecodeNode>? {
        val tlv = runCatching { Hex.decode(field.value) }.getOrNull() ?: return null
        if (tlv.isEmpty() || field.valueLength % tlv.size != 0) return null
        val scale = field.valueLength / tlv.size
        val delta = field.valueOffset
        val result = emvDecoder.decode(tlv, reveal = reveal)
        EmvTree.maskedRanges(result).forEach { masked += delta + it.first * scale until delta + (it.last + 1) * scale }
        return EmvTree.build(result, tlv.size).children.map { it.relocated(delta, scale) }
    }

    private fun maskRule(id: Int): (String) -> String = when (id) {
        2 -> Masking::pan
        35 -> Masking::track2
        else -> Masking::full
    }
}
