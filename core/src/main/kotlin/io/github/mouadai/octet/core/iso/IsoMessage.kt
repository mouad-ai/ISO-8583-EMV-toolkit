package io.github.mouadai.octet.core.iso

import java.util.SortedMap
import java.util.SortedSet

/**
 * A decoded (possibly partial) ISO 8583 message. Every element keeps its byte [offset][FieldValue.offset]
 * and length in [raw], the complete input buffer including framing (SPEC 6.1).
 */
class IsoMessage(
    val dialect: Dialect,
    val framing: FramingSpec,
    /** Declared frame length from a length prefix, when the framing has one. */
    val frameLength: Int?,
    /** Header / TPDU bytes, when the framing has them. */
    val header: ByteArray?,
    val mti: Mti?,
    val bitmap: Bitmap?,
    val fields: SortedMap<Int, FieldValue>,
    val raw: ByteArray,
) {
    /** Plain values for re-encoding with [IsoEncoder], e.g. `encode(decode(x).toData()) == x`. */
    fun toData(): IsoMessageData = IsoMessageData(
        mti = mti?.code ?: "",
        fields = fields.mapValues { it.value.value }.toSortedMap(),
        header = header,
    )
}

/**
 * One decoded data element or subfield.
 *
 * [offset]/[length] cover the whole element in the original buffer, length prefix included;
 * [prefixLength] is the size of that prefix, so the value itself starts at `offset + prefixLength`.
 * [value] is the decoded text: characters for text fields, uppercase hex for raw binary.
 */
class FieldValue(
    val id: String,
    val name: String,
    val raw: ByteArray,
    val value: String,
    val offset: Int,
    val length: Int,
    val prefixLength: Int = 0,
    val sensitive: Boolean = false,
    val children: List<FieldValue> = emptyList(),
) {
    val valueOffset: Int get() = offset + prefixLength
    val valueLength: Int get() = length - prefixLength

    /** Copy with offsets shifted by [delta] and lengths multiplied by [scale], recursively. */
    fun relocated(delta: Int, scale: Int = 1): FieldValue = FieldValue(
        id, name, raw, value,
        offset = delta + offset * scale,
        length = length * scale,
        prefixLength = prefixLength * scale,
        sensitive = sensitive,
        children = children.map { it.relocated(delta, scale) },
    )

    override fun toString(): String = "FieldValue($id=$value @$offset+$length)"
}

/** The four MTI digits with their human-readable meaning (SPEC 6.2). */
class Mti(val code: String, val offset: Int, val length: Int, val raw: ByteArray) {
    val version: String get() = MtiLabels.version(code[0])
    val messageClass: String get() = MtiLabels.messageClass(code[1])
    val function: String get() = MtiLabels.function(code[2])
    val origin: String get() = MtiLabels.origin(code[3])

    /** e.g. "1987 / Authorization / Request / Acquirer". */
    val description: String get() = "$version / $messageClass / $function / $origin"

    override fun toString(): String = "Mti($code: $description)"
}

object MtiLabels {
    fun version(c: Char): String = when (c) {
        '0' -> "1987"
        '1' -> "1993"
        '2' -> "2003"
        '8' -> "National use"
        '9' -> "Private use"
        else -> "Reserved ($c)"
    }

    fun messageClass(c: Char): String = when (c) {
        '1' -> "Authorization"
        '2' -> "Financial"
        '3' -> "File action"
        '4' -> "Reversal/Chargeback"
        '5' -> "Reconciliation"
        '6' -> "Administrative"
        '7' -> "Fee collection"
        '8' -> "Network management"
        else -> "Reserved ($c)"
    }

    fun function(c: Char): String = when (c) {
        '0' -> "Request"
        '1' -> "Request response"
        '2' -> "Advice"
        '3' -> "Advice response"
        '4' -> "Notification"
        '5' -> "Notification acknowledgement"
        '6' -> "Instruction"
        '7' -> "Instruction acknowledgement"
        else -> "Reserved ($c)"
    }

    fun origin(c: Char): String = when (c) {
        '0' -> "Acquirer"
        '1' -> "Acquirer repeat"
        '2' -> "Issuer"
        '3' -> "Issuer repeat"
        '4' -> "Other"
        '5' -> "Other repeat"
        else -> "Reserved ($c)"
    }
}

/** Primary, secondary and tertiary bitmaps as one element spanning [offset]..[offset]+[length]. */
class Bitmap(val present: SortedSet<Int>, val offset: Int, val length: Int, val raw: ByteArray) {
    /** Data element numbers, without the bitmap-indicator bits 1 and (when tertiary) 65. */
    val dataFields: List<Int> get() = present.toList()

    override fun toString(): String = "Bitmap(${present.joinToString(",")})"
}

/** Where and why decoding stopped or went wrong (SPEC 6.7). [message] is ready to show to a user. */
data class DecodeError(val message: String, val offset: Int, val fieldId: Int? = null)

/** Result of [IsoDecoder.decode]: always a message (possibly partial) plus any errors. Never throws. */
class DecodeResult(val message: IsoMessage, val errors: List<DecodeError>) {
    val isComplete: Boolean get() = errors.isEmpty()
}

/** Input to [IsoEncoder]: values in the same text form [IsoDecoder] produces. */
data class IsoMessageData(
    val mti: String,
    val fields: Map<Int, String>,
    val header: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean = other is IsoMessageData && mti == other.mti &&
        fields == other.fields && (header?.contentEquals(other.header) ?: (other.header == null))

    override fun hashCode(): Int = 31 * (31 * mti.hashCode() + fields.hashCode()) + (header?.contentHashCode() ?: 0)
}

data class EncodeError(val message: String, val fieldId: Int? = null)

sealed interface EncodeResult {
    data class Success(val bytes: ByteArray) : EncodeResult {
        override fun equals(other: Any?): Boolean = other is Success && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    data class Failure(val errors: List<EncodeError>) : EncodeResult
}

internal fun hexOffset(offset: Int): String = "0x%02X".format(offset)
