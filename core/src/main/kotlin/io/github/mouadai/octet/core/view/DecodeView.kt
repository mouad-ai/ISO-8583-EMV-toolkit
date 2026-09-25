package io.github.mouadai.octet.core.view

import io.github.mouadai.octet.core.emv.EmvDecoder

/** What the input bytes should be decoded as. */
enum class DecodeMode(val label: String) {
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

    fun decode(bytes: ByteArray, mode: DecodeMode, reveal: Boolean): DecodeViewModel = when (mode) {
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
