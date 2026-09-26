package io.github.mouadai.cardwire.core.view

import io.github.mouadai.cardwire.core.emv.EmvDecoder
import io.github.mouadai.cardwire.core.iso.Dialect
import io.github.mouadai.cardwire.core.iso.FramingSpec
import io.github.mouadai.cardwire.core.iso.IsoDecoder

/** What the input bytes should be decoded as. */
enum class DecodeMode(val label: String) {
    ISO_8583("ISO 8583"),
    EMV_TLV("EMV TLV"),
    RAW("Raw bytes"),
}

/** Everything the decode tool window renders for one input. */
data class DecodeViewModel(
    val root: DecodeNode,
    /** Byte ranges to hide in the hex view. */
    val maskedRanges: List<IntRange>,
    /** Structured parse errors, already worded for display. */
    val problems: List<String>,
)

/** Single entry point for the tool window: bytes in, display tree out. Never throws on bad input. */
object DecodeView {

    private val emvDecoder = EmvDecoder()
    private val isoDecoder = IsoDecoder()
    private val isoTree = IsoTree(emvDecoder)

    /**
     * @param dialect required for [DecodeMode.ISO_8583]
     * @param framing null to auto-detect among the dialect's framings
     */
    fun decode(
        bytes: ByteArray,
        mode: DecodeMode,
        reveal: Boolean,
        dialect: Dialect? = null,
        framing: FramingSpec? = null,
    ): DecodeViewModel = when (mode) {
        DecodeMode.ISO_8583 -> {
            requireNotNull(dialect) { "ISO 8583 decoding needs a dialect" }
            val result = isoDecoder.decode(bytes, dialect, framing)
            val tree = isoTree.build(result, reveal)
            DecodeViewModel(tree.root, tree.maskedRanges, result.errors.map { it.message })
        }
        DecodeMode.RAW -> DecodeViewModel(
            root = DecodeNode("Input", "${bytes.size} bytes", "", offset = 0, length = bytes.size),
            maskedRanges = emptyList(),
            problems = emptyList(),
        )
        DecodeMode.EMV_TLV -> {
            val result = emvDecoder.decode(bytes, reveal = reveal)
            DecodeViewModel(
                root = EmvTree.build(result, bytes.size),
                maskedRanges = EmvTree.maskedRanges(result),
                problems = result.errors.map { it.message },
            )
        }
    }
}
