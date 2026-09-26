package io.github.mouadai.octet.core.iso

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Hand-crafted messages (SPEC 11): public test PAN 4111 1111 1111 1111, obviously fake values elsewhere. */
class DecoderTest {

    private val decoder = IsoDecoder()

    /** 0100 with fields 2, 3, 4, 7, 11, 14, 22, 35, 41, 49, 55 in the 1987 ASCII dialect. */
    private val ascii0100 = "0100" +
        "7224040020808200" +
        "16" + "4111111111111111" +
        "000000" +
        "000000001000" +
        "0925221000" +
        "123456" +
        "2912" +
        "051" +
        "34" + "4111111111111111=29122010000000000" +
        "TERM0001" +
        "504" +
        "014" + "9F02060000000010005F2A020504"

    @Test
    fun `decodes a 1987 ASCII authorization request`() {
        val message = decoder.decode(ascii(ascii0100), ascii87).complete()
        assertEquals("0100", message.mti!!.code)
        assertEquals("1987 / Authorization / Request / Acquirer", message.mti!!.description)
        assertEquals(listOf(2, 3, 4, 7, 11, 14, 22, 35, 41, 49, 55), message.bitmap!!.dataFields)
        assertEquals(FramingSpec.NONE, message.framing)
        val values = message.fields.mapValues { it.value.value }
        assertEquals("4111111111111111", values[2])
        assertEquals("000000001000", values[4])
        assertEquals("4111111111111111=29122010000000000", values[35])
        assertEquals("TERM0001", values[41])
        assertEquals("9F02060000000010005F2A020504", values[55])
        assertTrue(message.fields[2]!!.sensitive)
        assertFalse(message.fields[3]!!.sensitive)
    }

    @Test
    fun `every element keeps its offset and length`() {
        val message = decoder.decode(ascii(ascii0100), ascii87).complete()
        assertEquals(0, message.mti!!.offset)
        assertEquals(4, message.bitmap!!.offset)
        assertEquals(16, message.bitmap!!.length)
        val pan = message.fields[2]!!
        assertEquals(20, pan.offset)
        assertEquals(18, pan.length)
        assertEquals(2, pan.prefixLength)
        assertEquals(22, pan.valueOffset)
        val track2 = message.fields[35]!!
        assertEquals(79, track2.offset)
        assertEquals(36, track2.length)
        assertEquals("34" + "4111111111111111=29122010000000000", String(track2.raw, Charsets.ISO_8859_1))
        val icc = message.fields[55]!!
        assertEquals(ascii0100.length - 31, icc.offset)
        assertEquals(31, icc.length)
    }

    @Test
    fun `round-trips the ASCII sample byte for byte`() {
        val message = decoder.decode(ascii(ascii0100), ascii87).complete()
        assertBytes(ascii(ascii0100), IsoEncoder.encode(message.toData(), ascii87).bytes())
    }

    @Test
    fun `truncated variable field reports length, remaining bytes and offset`() {
        val truncated = ascii(ascii0100.substring(0, 79 + 2 + 12))
        val result = decoder.decode(truncated, ascii87, FramingSpec.NONE)
        assertEquals(
            listOf(DecodeError("Field 35 (Track 2 data): LLVAR length 34 exceeds remaining 12 bytes at offset 0x4F.", 79, 35)),
            result.errors,
        )
        // Everything before the bad field is still decoded.
        assertEquals(listOf(2, 3, 4, 7, 11, 14, 22), result.message.fields.keys.toList())
    }

    @Test
    fun `truncated fixed field`() {
        val result = decoder.decode(ascii(ascii0100.substring(0, 40)), ascii87, FramingSpec.NONE)
        assertEquals("Field 3 (Processing code): fixed length 6 exceeds remaining 2 bytes at offset 0x26.", result.errors.single().message)
    }

    @Test
    fun `corrupted inputs produce errors, not exceptions`() {
        val badBitmap = decoder.decode(ascii("0100" + "72240400208082ZZ"), ascii87, FramingSpec.NONE)
        assertEquals("Primary bitmap '72240400208082ZZ' is not hexadecimal at offset 0x04.", badBitmap.errors.single().message)
        assertNull(badBitmap.message.bitmap)

        val badMti = decoder.decode(ascii("01A0"), ascii87, FramingSpec.NONE)
        assertEquals(0, badMti.errors.single().offset)

        val badPrefix = decoder.decode(ascii("0100" + "4000000000000000" + "1X4111"), ascii87, FramingSpec.NONE)
        assertEquals("Field 2 (Primary account number): LLVAR length prefix '1X' is not numeric at offset 0x14.", badPrefix.errors.single().message)

        val tooLong = decoder.decode(ascii("0100" + "4000000000000000" + "20" + "4".repeat(20)), ascii87, FramingSpec.NONE)
        assertEquals("Field 2 (Primary account number): LLVAR length 20 exceeds maximum 19 at offset 0x14.", tooLong.errors.single().message)

        val trailing = decoder.decode(ascii("0800" + "0020000000000000" + "000001" + "XY"), ascii87, FramingSpec.NONE)
        assertEquals("2 unexpected trailing byte(s) after the last field at offset 0x1A.", trailing.errors.single().message)

        assertEquals(1, decoder.decode(ByteArray(0), ascii87).errors.size)
    }

    @Test
    fun `undefined field in bitmap is reported`() {
        val dialect = loadDialect(
            """{"id": "t", "name": "Tiny", "mti": {"encoding": "ASCII"}, "bitmap": {"encoding": "HEX_ASCII"},
               "fields": {"3": {"name": "Processing code", "type": "n", "lengthType": "FIXED", "maxLength": 6}}}""",
        )
        val result = decoder.decode(ascii("0200" + "6000000000000000" + "000000"), dialect)
        assertEquals("Field 2 is present in the bitmap but not defined in dialect 'Tiny' at offset 0x14.", result.errors.single().message)
    }

    /** 0100 with fields 2, 3, 4, 11, 35, 55 in the 1987 binary dialect, framed by a length and a TPDU. */
    private val binary0100 = "0100" +
        "7020000020000200" +
        "16" + "4111111111111111" +
        "000000" +
        "000000001000" +
        "123456" +
        "23" + "4111111111111111D291220F" +
        "0014" + "9F02060000000010005F2A020504"
    private val tpdu = "6000010000"

    @Test
    fun `decodes a 1987 binary message and detects its framing`() {
        val framed = hex("0041" + tpdu + binary0100)
        val message = decoder.decode(framed, binary87).complete()
        assertEquals(FramingSpec(LengthPrefix.BINARY_2, 5), message.framing)
        assertEquals(65, message.frameLength)
        assertEquals(tpdu, message.header!!.toHexString())
        assertEquals(7, message.mti!!.offset)
        assertEquals("0100", message.mti!!.code)
        assertEquals("4111111111111111=291220", message.fields[35]!!.value)
        assertEquals("000000001000", message.fields[4]!!.value)
        assertEquals("9F02060000000010005F2A020504", message.fields[55]!!.value)
        assertBytes(framed, IsoEncoder.encode(message.toData(), binary87, message.framing).bytes())
    }

    @Test
    fun `framing detection picks the unframed layout for unframed bytes`() {
        val message = decoder.decode(hex(binary0100), binary87).complete()
        assertEquals(FramingSpec.NONE, message.framing)
        assertNull(message.header)
    }

    @Test
    fun `wrong frame length is reported`() {
        val result = decoder.decode(hex("0040" + tpdu + binary0100), binary87, FramingSpec(LengthPrefix.BINARY_2, 5))
        assertEquals("Frame length prefix says 64 bytes but 65 follow at offset 0x00.", result.errors.single().message)
    }

    @Test
    fun `ASCII length framing`() {
        val framed = ascii("%04d".format(ascii0100.length) + ascii0100)
        val message = decoder.decode(framed, ascii87).complete()
        assertEquals(FramingSpec(LengthPrefix.ASCII_4), message.framing)
        assertEquals(4, message.mti!!.offset)
    }

    /** 1100 with a secondary bitmap: fields 2, 3, 4, 12, 24 and 128 in the 1993 ASCII dialect. */
    @Test
    fun `decodes a 1993 message with a secondary bitmap`() {
        val text = "1100" + "F010010000000000" + "0000000000000001" +
            "16" + "4111111111111111" + "000000" + "000000005000" + "260925221000" + "100" + "0123456789ABCDEF"
        val message = decoder.decode(ascii(text), ascii93).complete()
        assertEquals("1993 / Authorization / Request / Acquirer", message.mti!!.description)
        assertEquals(listOf(2, 3, 4, 12, 24, 128), message.bitmap!!.dataFields)
        assertEquals(32, message.bitmap!!.length)
        assertEquals("0123456789ABCDEF", message.fields[128]!!.value)
        assertBytes(ascii(text), IsoEncoder.encode(message.toData(), ascii93).bytes())
    }

    @Test
    fun `tertiary bitmap in an EBCDIC dialect`() {
        val dialect = loadDialect(
            """{"id": "e", "name": "EBCDIC test", "mti": {"encoding": "EBCDIC"},
               "bitmap": {"encoding": "HEX_EBCDIC", "tertiary": true},
               "fields": {
                 "3": {"name": "Processing code", "type": "n", "lengthType": "FIXED", "maxLength": 6, "dataEncoding": "EBCDIC"},
                 "130": {"name": "Private", "type": "ans", "lengthType": "LLVAR", "maxLength": 20,
                         "lengthEncoding": "EBCDIC", "dataEncoding": "EBCDIC"}
               }}""",
        )
        val data = IsoMessageData("0200", mapOf(3 to "000000", 130 to "Hello"))
        val bytes = IsoEncoder.encode(data, dialect).bytes()
        // Primary: bits 1 and 3 -> A0; secondary: bit 65 -> 80; tertiary: bit 130 -> 40.
        val primary = "A000000000000000".map { "F" + it }.joinToString("").replace("FA", "C1")
        val secondary = "8000000000000000".map { "F" + it }.joinToString("")
        val tertiary = "4000000000000000".map { "F" + it }.joinToString("")
        assertEquals(
            "F0F2F0F0" + primary + secondary + tertiary + "F0F0F0F0F0F0" + "F0F5" + "C885939396",
            bytes.toHexString(),
        )
        val message = decoder.decode(bytes, dialect).complete()
        assertEquals(listOf(3, 130), message.bitmap!!.dataFields)
        assertEquals("Hello", message.fields[130]!!.value)
    }

    @Test
    fun `fixed and private TLV subfields keep offsets`() {
        val dialect = loadDialect(
            """{"id": "s", "name": "Subfields", "mti": {"encoding": "ASCII"}, "bitmap": {"encoding": "HEX_ASCII"},
               "fields": {
                 "3": {"name": "Processing code", "type": "n", "lengthType": "FIXED", "maxLength": 6,
                  "subfields": {"layout": "FIXED", "fields": [
                    {"name": "Transaction type", "length": 2},
                    {"name": "From account", "length": 2},
                    {"name": "To account", "length": 2}]}},
                 "48": {"name": "Private data", "type": "ans", "lengthType": "LLLVAR", "maxLength": 999,
                  "subfields": {"layout": "PRIVATE_TLV", "tagLength": 2, "lengthLength": 2, "tags": {"02": "Loyalty id"}}}
               }}""",
        )
        val text = "0100" + "2000000000010000" + "003000" + "019" + "0103ABC" + "02" + "08" + "XYZ12345"
        val message = decoder.decode(ascii(text), dialect).complete()
        val code = message.fields[3]!!.children
        assertEquals(listOf("3.1", "3.2", "3.3"), code.map { it.id })
        assertEquals(listOf("00", "30", "00"), code.map { it.value })
        assertEquals(listOf(20, 22, 24), code.map { it.offset })
        val tlv = message.fields[48]!!.children
        assertEquals(listOf("48.01", "48.02"), tlv.map { it.id })
        assertEquals(listOf("ABC", "XYZ12345"), tlv.map { it.value })
        assertEquals(listOf("Tag 01", "Loyalty id"), tlv.map { it.name })
        assertEquals(29, tlv[0].offset)
        assertEquals(7, tlv[0].length)
        assertEquals(4, tlv[0].prefixLength)
        assertEquals(36, tlv[1].offset)
    }

    @Test
    fun `malformed private TLV keeps the field and reports the subfield error`() {
        val dialect = loadDialect(
            """{"id": "s", "name": "Subfields", "mti": {"encoding": "ASCII"}, "bitmap": {"encoding": "HEX_ASCII"},
               "fields": {"48": {"name": "Private data", "type": "ans", "lengthType": "LLLVAR", "maxLength": 999,
                  "subfields": {"layout": "PRIVATE_TLV", "tagLength": 2, "lengthLength": 2}}}}""",
        )
        val result = decoder.decode(ascii("0100" + "0000000000010000" + "007" + "0109ABC"), dialect)
        assertEquals("Field 48 (Private data): TLV tag '01' length 9 exceeds remaining 3 characters at offset 0x17.", result.errors.single().message)
        assertEquals("0109ABC", result.message.fields[48]!!.value)
    }

    @Test
    fun `field 55 splits into EMV tags at their positions in the buffer`() {
        val ascii = decoder.decode(ascii(ascii0100), ascii87).complete()
        val icc = ascii.fields[55]!!
        val tags = icc.children
        assertEquals(listOf("9F02", "5F2A"), tags.map { it.id })
        assertEquals(listOf("Amount, Authorised (Numeric)", "Transaction Currency Code"), tags.map { it.name })
        assertEquals(listOf("000000001000", "0504"), tags.map { it.value })
        // Hex text in the ASCII dialect: two characters per TLV byte.
        assertEquals(listOf(icc.valueOffset, icc.valueOffset + 18), tags.map { it.offset })
        assertEquals(listOf(18, 10), tags.map { it.length })
        assertEquals(6, tags[0].prefixLength)

        val binary = decoder.decode(hex(binary0100), binary87).complete()
        val binaryIcc = binary.fields[55]!!
        assertEquals(listOf(binaryIcc.valueOffset, binaryIcc.valueOffset + 9), binaryIcc.children.map { it.offset })
        assertEquals(listOf(9, 5), binaryIcc.children.map { it.length })
        assertBytes(hex("5F2A020504"), binaryIcc.children[1].raw)
    }

    @Test
    fun `sensitive EMV tags are flagged`() {
        val text = "0100" + "0000000000000200" + "010" + "5A084111111111111111"
        val pan = decoder.decode(ascii(text), ascii87).complete().fields[55]!!.children.single()
        assertEquals("5A", pan.id)
        assertTrue(pan.sensitive)
    }

    @Test
    fun `malformed field 55 keeps the field and reports the TLV error in message offsets`() {
        val text = "0100" + "0000000000000200" + "006" + "9F0206000000"
        val result = decoder.decode(ascii(text), ascii87, FramingSpec.NONE)
        val error = result.errors.single()
        assertEquals(55, error.fieldId)
        assertTrue(error.message.startsWith("Field 55 (ICC data (EMV)): "), error.message)
        assertTrue(error.message.endsWith(" at offset ${"0x%02X".format(error.offset)}."), error.message)
        assertTrue(error.offset in 23 until text.length, "offset ${error.offset}")
        assertEquals("9F0206000000", result.message.fields[55]!!.value)
    }

    @Test
    fun `BER-TLV decoding can be replaced or turned off`() {
        val stub = BerTlvSubfieldDecoder { data ->
            SubfieldDecodeResult(listOf(FieldValue("X", "Stub", data.copyOfRange(0, 2), "stub", offset = 1, length = 2)))
        }
        val stubbed = IsoDecoder(stub).decode(ascii(ascii0100), ascii87).complete().fields[55]!!
        assertEquals(stubbed.valueOffset + 2, stubbed.children.single().offset)
        assertEquals(4, stubbed.children.single().length)
        assertTrue(IsoDecoder(null).decode(hex(binary0100), binary87).complete().fields[55]!!.children.isEmpty())
    }

    private val bitmappedDialect by lazy {
        loadDialect(
            """{"id": "bm", "name": "Bitmapped", "mti": {"encoding": "ASCII"}, "bitmap": {"encoding": "HEX_ASCII"},
               "fields": {"127": {"name": "Private data", "type": "ans", "lengthType": "LLLVAR", "maxLength": 999,
                  "subfields": {"layout": "BITMAP", "bitmapLength": 8, "bitmapEncoding": "HEX_ASCII", "fields": {
                    "2": {"name": "Switch key", "type": "n", "lengthType": "FIXED", "maxLength": 6},
                    "3": {"name": "Routing info", "type": "ans", "lengthType": "LLVAR", "maxLength": 20}}}}}}""",
        )
    }

    @Test
    fun `bitmap-driven subfields decode with offsets`() {
        val text = "0100" + "8000000000000000" + "0000000000000002" + "029" + "6000000000000000" + "123456" + "05" + "Hello"
        val message = decoder.decode(ascii(text), bitmappedDialect).complete()
        val children = message.fields[127]!!.children
        assertEquals(listOf("127.bitmap", "127.2", "127.3"), children.map { it.id })
        assertEquals(listOf("2,3", "123456", "Hello"), children.map { it.value })
        assertEquals(listOf(39, 55, 61), children.map { it.offset })
        assertEquals(listOf(16, 6, 7), children.map { it.length })
        assertEquals(2, children[2].prefixLength)
        assertBytes(ascii(text), IsoEncoder.encode(message.toData(), bitmappedDialect).bytes())
    }

    @Test
    fun `bitmap-driven subfield errors name the subfield and keep the field`() {
        val undefined = "0100" + "8000000000000000" + "0000000000000002" + "022" + "5000000000000000" + "123456"
        val result = decoder.decode(ascii(undefined), bitmappedDialect)
        assertEquals("Field 127 (Private data): subfield 4 is present in the bitmap but not defined at offset 0x3D.", result.errors.single().message)
        assertEquals(127, result.errors.single().fieldId)
        assertEquals(listOf("127.bitmap", "127.2"), result.message.fields[127]!!.children.map { it.id })

        val truncated = "0100" + "8000000000000000" + "0000000000000002" + "023" + "2000000000000000" + "08" + "Hello"
        val error = decoder.decode(ascii(truncated), bitmappedDialect).errors.single()
        assertEquals("Field 127.3 (Routing info): LLVAR length 8 exceeds remaining 5 bytes at offset 0x37.", error.message)
    }
}
