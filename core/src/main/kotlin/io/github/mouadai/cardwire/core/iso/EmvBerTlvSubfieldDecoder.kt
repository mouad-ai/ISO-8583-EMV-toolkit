package io.github.mouadai.cardwire.core.iso

import io.github.mouadai.cardwire.core.emv.BerTlv
import io.github.mouadai.cardwire.core.emv.EmvTagDictionary
import io.github.mouadai.cardwire.core.emv.TlvNode

/**
 * Splits field 55 into its EMV tags with the M1 [BerTlv] parser. Each child's value is the tag's raw
 * value as hex; names and sensitivity come from the EMV tag dictionary. Formatting and bit-level
 * meanings are left to `EmvDecoder`, which the UI applies to the field's bytes.
 */
class EmvBerTlvSubfieldDecoder(
    private val dictionary: EmvTagDictionary = EmvTagDictionary.default,
) : BerTlvSubfieldDecoder {

    override fun decode(data: ByteArray): SubfieldDecodeResult {
        val parsed = BerTlv.decode(data)
        return SubfieldDecodeResult(
            children = parsed.nodes.map(::toFieldValue),
            errors = parsed.errors.map { SubfieldError(it.offset, it.message) },
        )
    }

    private fun toFieldValue(node: TlvNode): FieldValue {
        val info = dictionary[node.tag]
        return FieldValue(
            id = node.tag.hex,
            name = info?.name ?: "Unknown tag",
            raw = node.tag.bytes + node.lengthBytes + node.value,
            value = node.valueHex,
            offset = node.offset,
            length = node.totalLength,
            prefixLength = node.headerLength,
            sensitive = info?.sensitive ?: false,
            children = node.children.map(::toFieldValue),
        )
    }
}
