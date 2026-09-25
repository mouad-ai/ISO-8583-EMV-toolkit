package io.github.mouadai.octet.core.iso

/**
 * A processor/network "dialect": how one flavour of ISO 8583 lays out its MTI, bitmaps, framing
 * and data elements. Loaded from JSON by [DialectLoader]; see SPEC sections 6 and 7.
 */
data class Dialect(
    val id: String,
    val name: String,
    val description: String,
    /** ISO 8583 version the layout follows ("1987", "1993", "2003"), when declared. */
    val version: String?,
    val mtiEncoding: MtiEncoding,
    val bitmap: BitmapSpec,
    /** Candidate framings tried in order by auto-detection. Never empty. */
    val framings: List<FramingSpec>,
    val fields: Map<Int, FieldSpec>,
) {
    fun field(id: Int): FieldSpec? = fields[id]
}

enum class MtiEncoding { ASCII, BCD, EBCDIC }

enum class BitmapEncoding {
    /** 8 raw bytes per bitmap. */
    BINARY,

    /** 16 hexadecimal ASCII characters per bitmap. */
    HEX_ASCII,

    /** 16 hexadecimal EBCDIC characters per bitmap. */
    HEX_EBCDIC,
}

/** [tertiary]: whether bit 65 announces a third bitmap (fields 129-192) instead of data element 65. */
data class BitmapSpec(val encoding: BitmapEncoding, val tertiary: Boolean = false)

enum class LengthPrefix {
    NONE,

    /** 2-byte unsigned big-endian length of everything that follows. */
    BINARY_2,

    /** 4 ASCII digits giving the length of everything that follows. */
    ASCII_4,
}

/**
 * Message framing in front of the MTI: an optional length prefix, then [headerLength] header bytes
 * (5 for a TPDU, or any custom header size).
 */
data class FramingSpec(val lengthPrefix: LengthPrefix = LengthPrefix.NONE, val headerLength: Int = 0) {
    init {
        require(headerLength >= 0) { "headerLength must not be negative" }
    }

    val label: String
        get() = buildList {
            when (lengthPrefix) {
                LengthPrefix.NONE -> {}
                LengthPrefix.BINARY_2 -> add("2-byte binary length")
                LengthPrefix.ASCII_4 -> add("4-byte ASCII length")
            }
            if (headerLength == 5) add("TPDU") else if (headerLength > 0) add("$headerLength-byte header")
        }.joinToString(" + ").ifEmpty { "none" }

    companion object {
        val NONE = FramingSpec()
    }
}

enum class FieldType(val code: String) {
    /** Numeric digits. */
    N("n"),

    /** Alphabetic. */
    A("a"),

    /** Alphanumeric. */
    AN("an"),

    /** Alphanumeric and special characters. */
    ANS("ans"),

    /** Binary; lengths count bytes. */
    B("b"),

    /** Track 2/3 code set: digits plus separator (`=`, or nibble `D` in BCD). */
    Z("z"),

    /** Sign character `C` or `D` followed by digits, e.g. `x+n8` amounts. Length counts the sign. */
    XN("xn"),

    /** Anything else; handled as `ans` when character-encoded and as raw bytes when binary. */
    CUSTOM("custom");

    companion object {
        fun fromCode(code: String): FieldType? = entries.firstOrNull { it.code == code }
    }
}

enum class LengthType(val digits: Int) {
    FIXED(0),
    LLVAR(2),
    LLLVAR(3),
    LLLLVAR(4),
}

enum class LengthEncoding { ASCII, BCD, BINARY, EBCDIC }

enum class DataEncoding {
    ASCII,

    /** Same as [BCD_LEFT_PAD]. */
    BCD,
    BCD_LEFT_PAD,
    BCD_RIGHT_PAD,
    EBCDIC,
    BINARY;

    val isBcd: Boolean get() = this == BCD || this == BCD_LEFT_PAD || this == BCD_RIGHT_PAD
    val isCharacter: Boolean get() = this == ASCII || this == EBCDIC
}

enum class PadSide { LEFT, RIGHT }

/**
 * How a value shorter than a FIXED field is padded when encoding, and which nibble fills an odd
 * digit count in BCD. Decoding never strips character padding, so re-encoding is byte-identical.
 */
data class Padding(val side: PadSide, val char: Char)

data class FieldSpec(
    val id: Int,
    val name: String,
    val type: FieldType,
    val lengthType: LengthType,
    /** Digits/characters for text types, bytes for [FieldType.B]. Exact length when [LengthType.FIXED]. */
    val maxLength: Int,
    val lengthEncoding: LengthEncoding,
    val dataEncoding: DataEncoding,
    val padding: Padding? = null,
    val sensitive: Boolean = false,
    val subfields: SubfieldLayout? = null,
) {
    /** "Field 35 (Track 2 data)", the prefix of every error message about this field. */
    val label: String get() = "Field $id ($name)"
}

/** How the value of a field splits into child elements (SPEC 6.4). */
sealed interface SubfieldLayout {
    /**
     * Consecutive slices. Each [SubfieldSpec.length] is in the parent's length units
     * (digits/characters, or bytes for binary fields). Decoding stops quietly when the value runs
     * out, since trailing subfields are often optional.
     */
    data class Fixed(val subfields: List<SubfieldSpec>) : SubfieldLayout

    /**
     * Private tag-length-value with character tags, as found in fields 48, 62, 63, 126, 127:
     * [tagLength] tag characters, [lengthLength] decimal length digits, then the value.
     * [tagNames] optionally names known tags.
     */
    data class PrivateTlv(
        val tagLength: Int,
        val lengthLength: Int,
        val tagNames: Map<String, String> = emptyMap(),
    ) : SubfieldLayout

    /** EMV BER-TLV (field 55). Children come from the [BerTlvSubfieldDecoder] given to [IsoDecoder]. */
    data object BerTlv : SubfieldLayout
}

data class SubfieldSpec(val id: String, val name: String, val length: Int, val sensitive: Boolean = false)
