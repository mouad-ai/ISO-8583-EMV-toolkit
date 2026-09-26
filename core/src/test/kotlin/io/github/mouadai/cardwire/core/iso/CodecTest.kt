package io.github.mouadai.cardwire.core.iso

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CodecTest {

    private fun spec(
        type: FieldType,
        dataEncoding: DataEncoding,
        maxLength: Int = 20,
        lengthType: LengthType = LengthType.LLVAR,
        padding: Padding? = null,
    ) = FieldSpec(99, "Test", type, lengthType, maxLength, LengthEncoding.ASCII, dataEncoding, padding)

    private fun roundTrip(spec: FieldSpec, value: String, expectedHex: String) {
        val (bytes, units) = FieldCodec.encodeValue(spec, value)
        assertEquals(expectedHex, bytes.toHexString())
        assertEquals(value, FieldCodec.decodeValue(spec, bytes, units))
        assertEquals(bytes.size, FieldCodec.byteCount(spec, units))
    }

    @Test
    fun `MTI in each encoding`() {
        assertBytes(ascii("0100"), HeaderCodec.encodeMti("0100", MtiEncoding.ASCII))
        assertBytes(hex("0100"), HeaderCodec.encodeMti("0100", MtiEncoding.BCD))
        assertBytes(hex("F0F2F0F0"), HeaderCodec.encodeMti("0200", MtiEncoding.EBCDIC))
        assertEquals("0200", HeaderCodec.decodeMti(hex("F0F2F0F0"), MtiEncoding.EBCDIC))
        assertEquals("0810", HeaderCodec.decodeMti(hex("0810"), MtiEncoding.BCD))
        assertThrows<CodecException> { HeaderCodec.decodeMti(hex("0A10"), MtiEncoding.BCD) }
    }

    @Test
    fun `MTI labels`() {
        val mti = Mti("0100", 0, 4, ascii("0100"))
        assertEquals("1987 / Authorization / Request / Acquirer", mti.description)
        assertEquals("1993 / Financial / Request response / Issuer", Mti("1212", 0, 4, ByteArray(4)).description)
        assertEquals("2003 / Network management / Advice / Acquirer repeat", Mti("2821", 0, 4, ByteArray(4)).description)
    }

    @Test
    fun `bitmaps in each encoding`() {
        val bits = setOf(2, 3, 4, 7, 11, 64)
        val binary = HeaderCodec.encodeBitmap(bits, BitmapEncoding.BINARY, 0)
        assertEquals("7220000000000001", binary.toHexString())
        assertBytes(ascii("7220000000000001"), HeaderCodec.encodeBitmap(bits, BitmapEncoding.HEX_ASCII, 0))
        assertEquals(bits.sorted(), HeaderCodec.decodeBitmap(binary, BitmapEncoding.BINARY, 0))
        val ebcdic = HeaderCodec.encodeBitmap(bits, BitmapEncoding.HEX_EBCDIC, 0)
        assertEquals("F7F2F2F0F0F0F0F0F0F0F0F0F0F0F0F1", ebcdic.toHexString())
        assertEquals(bits.sorted(), HeaderCodec.decodeBitmap(ebcdic, BitmapEncoding.HEX_EBCDIC, 0))
        assertEquals(listOf(66, 128), HeaderCodec.decodeBitmap(hex("4000000000000001"), BitmapEncoding.BINARY, 64))
        assertThrows<CodecException> { HeaderCodec.decodeBitmap(ascii("72200000000000G1"), BitmapEncoding.HEX_ASCII, 0) }
    }

    @Test
    fun `secondary and tertiary bitmaps are added when needed`() {
        val spec = BitmapSpec(BitmapEncoding.BINARY, tertiary = true)
        assertEquals("4000000000000000", HeaderCodec.encodeBitmaps(setOf(2), spec).toHexString())
        assertEquals("C000000000000000" + "0000000000000001", HeaderCodec.encodeBitmaps(setOf(2, 128), spec).toHexString())
        assertEquals(
            "8000000000000000" + "8000000000000000" + "8000000000000000",
            HeaderCodec.encodeBitmaps(setOf(129), spec).toHexString(),
        )
        assertThrows<CodecException> { HeaderCodec.encodeBitmaps(setOf(129), spec.copy(tertiary = false)) }
    }

    @Test
    fun `length prefixes`() {
        assertBytes(ascii("07"), FieldCodec.encodePrefix(7, LengthType.LLVAR, LengthEncoding.ASCII))
        assertBytes(ascii("007"), FieldCodec.encodePrefix(7, LengthType.LLLVAR, LengthEncoding.ASCII))
        assertBytes(ascii("0007"), FieldCodec.encodePrefix(7, LengthType.LLLLVAR, LengthEncoding.ASCII))
        assertBytes(hex("F1F9"), FieldCodec.encodePrefix(19, LengthType.LLVAR, LengthEncoding.EBCDIC))
        assertBytes(hex("19"), FieldCodec.encodePrefix(19, LengthType.LLVAR, LengthEncoding.BCD))
        assertBytes(hex("0255"), FieldCodec.encodePrefix(255, LengthType.LLLVAR, LengthEncoding.BCD))
        assertBytes(hex("13"), FieldCodec.encodePrefix(19, LengthType.LLVAR, LengthEncoding.BINARY))
        assertBytes(hex("00FF"), FieldCodec.encodePrefix(255, LengthType.LLLVAR, LengthEncoding.BINARY))
        assertEquals(255, FieldCodec.decodePrefix(hex("0255"), LengthType.LLLVAR, LengthEncoding.BCD))
        assertEquals(19, FieldCodec.decodePrefix(hex("F1F9"), LengthType.LLVAR, LengthEncoding.EBCDIC))
        assertEquals(1000, FieldCodec.decodePrefix(hex("03E8"), LengthType.LLLVAR, LengthEncoding.BINARY))
        assertThrows<CodecException> { FieldCodec.encodePrefix(100, LengthType.LLVAR, LengthEncoding.ASCII) }
        assertThrows<CodecException> { FieldCodec.decodePrefix(ascii("1A"), LengthType.LLVAR, LengthEncoding.ASCII) }
        assertThrows<CodecException> { FieldCodec.decodePrefix(hex("1A"), LengthType.LLVAR, LengthEncoding.BCD) }
    }

    @Test
    fun `numeric BCD pads odd lengths`() {
        roundTrip(spec(FieldType.N, DataEncoding.BCD_LEFT_PAD), "123", "0123")
        roundTrip(spec(FieldType.N, DataEncoding.BCD), "1234", "1234")
        roundTrip(spec(FieldType.N, DataEncoding.BCD_RIGHT_PAD), "123", "123F")
        roundTrip(spec(FieldType.N, DataEncoding.BCD_RIGHT_PAD, padding = Padding(PadSide.RIGHT, '0')), "123", "1230")
    }

    @Test
    fun `track 2 in BCD maps the separator to nibble D`() {
        roundTrip(spec(FieldType.Z, DataEncoding.BCD_RIGHT_PAD, 37), "4111111111111111=291220", "4111111111111111D291220F")
        roundTrip(spec(FieldType.Z, DataEncoding.ASCII, 37), "4111=29", "343131313D3239")
    }

    @Test
    fun `signed amounts`() {
        roundTrip(spec(FieldType.XN, DataEncoding.ASCII, 9, LengthType.FIXED), "C00000100", "433030303030313030")
        roundTrip(spec(FieldType.XN, DataEncoding.BCD, 9, LengthType.FIXED), "D00000100", "4400000100")
        assertThrows<CodecException> { FieldCodec.encodeValue(spec(FieldType.XN, DataEncoding.ASCII, 9, LengthType.FIXED), "X00000100") }
    }

    @Test
    fun `binary fields as raw bytes or hex text`() {
        roundTrip(spec(FieldType.B, DataEncoding.BINARY, 8, LengthType.FIXED), "0123456789ABCDEF", "0123456789ABCDEF")
        roundTrip(spec(FieldType.B, DataEncoding.ASCII, 8, LengthType.FIXED), "0123456789ABCDEF", ascii("0123456789ABCDEF").toHexString())
        assertEquals(16, FieldCodec.byteCount(spec(FieldType.B, DataEncoding.ASCII, 8, LengthType.FIXED), 8))
        assertThrows<CodecException> { FieldCodec.encodeValue(spec(FieldType.B, DataEncoding.BINARY), "ABC") }
    }

    @Test
    fun `text in ASCII and EBCDIC`() {
        roundTrip(spec(FieldType.ANS, DataEncoding.ASCII), "Hi 1!", "4869203121")
        roundTrip(spec(FieldType.ANS, DataEncoding.EBCDIC), "Hi 1!", "C88940F15A")
    }

    @Test
    fun `fixed fields pad short values and reject long ones`() {
        val n = spec(FieldType.N, DataEncoding.ASCII, 6, LengthType.FIXED)
        assertEquals("000042", FieldCodec.decodeValue(n, FieldCodec.encodeValue(n, "42").first, 6))
        val an = spec(FieldType.AN, DataEncoding.ASCII, 4, LengthType.FIXED)
        assertEquals("AB  ", FieldCodec.decodeValue(an, FieldCodec.encodeValue(an, "AB").first, 4))
        val custom = spec(FieldType.ANS, DataEncoding.ASCII, 4, LengthType.FIXED, Padding(PadSide.LEFT, '*'))
        assertEquals("**AB", FieldCodec.decodeValue(custom, FieldCodec.encodeValue(custom, "AB").first, 4))
        assertThrows<CodecException> { FieldCodec.encodeValue(n, "1234567") }
        assertThrows<CodecException> { FieldCodec.encodeValue(n, "12A") }
        assertThrows<CodecException> { FieldCodec.encodeValue(spec(FieldType.N, DataEncoding.ASCII, 3), "1234") }
    }
}
