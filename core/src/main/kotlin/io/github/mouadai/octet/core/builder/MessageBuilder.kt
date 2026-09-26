package io.github.mouadai.octet.core.builder

import io.github.mouadai.octet.core.emv.BerTlv
import io.github.mouadai.octet.core.iso.CodecException
import io.github.mouadai.octet.core.iso.DataEncoding
import io.github.mouadai.octet.core.iso.Dialect
import io.github.mouadai.octet.core.iso.EncodeError
import io.github.mouadai.octet.core.iso.EncodeResult
import io.github.mouadai.octet.core.iso.FieldCodec
import io.github.mouadai.octet.core.iso.FieldSpec
import io.github.mouadai.octet.core.iso.FieldType
import io.github.mouadai.octet.core.iso.FramingSpec
import io.github.mouadai.octet.core.iso.IsoEncoder
import io.github.mouadai.octet.core.iso.IsoMessageData
import io.github.mouadai.octet.core.iso.LengthType
import io.github.mouadai.octet.core.iso.SubfieldLayout

/** Outcome of [MessageBuilder.build]: the encoded bytes, or every problem that blocks encoding. */
class BuildResult(val bytes: ByteArray?, val errors: List<EncodeError>, val data: IsoMessageData) {
    val isSuccess: Boolean get() = bytes != null
}

/**
 * Backs the dialect-driven builder form (SPEC 8.3): describes each field for the form, checks values
 * one at a time as the user types, and encodes the whole message with [IsoEncoder].
 * Pure and offline; never throws on user input.
 */
class MessageBuilder(val dialect: Dialect) {

    /** Data elements the form offers, in field-number order. */
    val fields: List<FieldSpec> get() = dialect.fields.values.sortedBy { it.id }

    /** Short description of a field's format for the form, e.g. "n 12, fixed" or "ans ..99, LLVAR". */
    fun formatHint(spec: FieldSpec): String {
        val unit = if (spec.type == FieldType.B) " bytes as hex" else ""
        return when (spec.lengthType) {
            LengthType.FIXED -> "${spec.type.code} ${spec.maxLength}$unit, fixed"
            else -> "${spec.type.code} ..${spec.maxLength}$unit, ${spec.lengthType.name}"
        }
    }

    /** Checks the 4-digit MTI; null when valid. */
    fun checkMti(mti: String): String? = when {
        mti.length != 4 -> "MTI must be 4 digits."
        !mti.all { it in '0'..'9' } -> "MTI must contain digits only."
        else -> null
    }

    /**
     * Checks one field value as it would be encoded; null when valid. Also applies the ISO character
     * classes (a, an, ans) that the codec itself leaves open, and checks that BER-TLV fields hold
     * well-formed TLV.
     */
    fun checkField(id: Int, value: String): String? {
        val spec = dialect.field(id) ?: return "Field $id is not defined in dialect '${dialect.name}'."
        characterClassProblem(spec, value)?.let { return "${spec.label}: $it." }
        try {
            val (_, units) = FieldCodec.encodeValue(spec, value)
            if (spec.lengthType != LengthType.FIXED) {
                FieldCodec.encodePrefix(units, spec.lengthType, spec.lengthEncoding)
            }
        } catch (e: CodecException) {
            return "${spec.label}: ${e.message}."
        }
        if (spec.subfields == SubfieldLayout.BerTlv) {
            val parsed = try {
                BerTlv.decode(value)
            } catch (e: IllegalArgumentException) {
                return "${spec.label}: EMV data must be hex."
            }
            parsed.errors.firstOrNull()?.let { return "${spec.label}: ${it.message}" }
        }
        return null
    }

    /**
     * Encodes the message. Every field is checked first so the form can show all problems at once,
     * each tagged with its field number.
     */
    fun build(
        mti: String,
        values: Map<Int, String>,
        framing: FramingSpec = FramingSpec.NONE,
        header: ByteArray? = null,
    ): BuildResult {
        val data = IsoMessageData(mti, values.toSortedMap(), header)
        val errors = buildList {
            checkMti(mti)?.let { add(EncodeError(it)) }
            for ((id, value) in values.toSortedMap()) checkField(id, value)?.let { add(EncodeError(it, id)) }
        }
        if (errors.isNotEmpty()) return BuildResult(null, errors, data)
        return when (val result = IsoEncoder.encode(data, dialect, framing)) {
            is EncodeResult.Success -> BuildResult(result.bytes, emptyList(), data)
            is EncodeResult.Failure -> BuildResult(null, result.errors, data)
        }
    }

    private fun characterClassProblem(spec: FieldSpec, value: String): String? {
        if (!spec.dataEncoding.isCharacter && spec.dataEncoding != DataEncoding.BINARY) return null
        val ok: (Char) -> Boolean = when (spec.type) {
            FieldType.A -> { c -> c in 'A'..'Z' || c in 'a'..'z' || c == ' ' }
            FieldType.AN -> { c -> c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == ' ' }
            FieldType.ANS -> { c -> c in ' '..'~' }
            else -> return null
        }
        val bad = value.firstOrNull { !ok(it) } ?: return null
        val what = when (spec.type) {
            FieldType.A -> "letters and spaces"
            FieldType.AN -> "letters, digits and spaces"
            else -> "printable ASCII characters"
        }
        return "'${if (bad in ' '..'~') bad.toString() else "\\u%04X".format(bad.code)}' is not allowed; type ${spec.type.code} takes $what"
    }
}
