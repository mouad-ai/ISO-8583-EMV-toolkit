package io.github.mouadai.cardwire.core.builder

import io.github.mouadai.cardwire.core.emv.Hex
import io.github.mouadai.cardwire.core.iso.BuiltinDialects
import io.github.mouadai.cardwire.core.iso.FramingSpec
import io.github.mouadai.cardwire.core.iso.IsoDecoder
import io.github.mouadai.cardwire.core.iso.LengthPrefix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageBuilderTest {
    private val ascii87 = MessageBuilder(BuiltinDialects.load("iso8583-1987-ascii"))
    private val binary87 = MessageBuilder(BuiltinDialects.load("iso8583-1987-binary"))

    // Hand-built field 55: amount 10.00, currency MAD, date 2026-09-25, TVR all zero.
    private val field55 = "9F02060000000010005F2A0205049A0326092595050000000000"

    @Test
    fun `builds a small ASCII message byte for byte`() {
        val result = ascii87.build("0100", mapOf(3 to "000000", 4 to "000000001000"))
        assertTrue(result.isSuccess, result.errors.toString())
        assertEquals("0100" + "3000000000000000" + "000000" + "000000001000", String(result.bytes!!, Charsets.US_ASCII))
    }

    @Test
    fun `builds the same message in the binary dialect`() {
        val result = binary87.build("0100", mapOf(3 to "000000", 4 to "000000001000"))
        assertEquals("0100" + "3000000000000000" + "000000" + "000000001000", Hex.encode(result.bytes!!))
    }

    @Test
    fun `fixed numeric fields are left padded with zeros`() {
        val result = ascii87.build("0100", mapOf(4 to "1000"))
        assertTrue(String(result.bytes!!, Charsets.US_ASCII).endsWith("000000001000"))
    }

    @Test
    fun `a built message decodes back to the same values, including field 55 tags`() {
        val values = mapOf(
            2 to "4111111111111111",
            3 to "000000",
            4 to "000000001000",
            11 to "000123",
            41 to "TERM0001",
            49 to "504",
            55 to field55,
        )
        for (builder in listOf(ascii87, binary87)) {
            val built = builder.build("0200", values)
            assertTrue(built.isSuccess, built.errors.toString())
            val decoded = IsoDecoder().decode(built.bytes!!, builder.dialect, FramingSpec.NONE)
            assertTrue(decoded.isComplete, decoded.errors.toString())
            assertEquals("0200", decoded.message.mti?.code)
            assertEquals(values, decoded.message.fields.mapValues { it.value.value })
            assertEquals(listOf("9F02", "5F2A", "9A", "95"), decoded.message.fields.getValue(55).children.map { it.id })
        }
    }

    @Test
    fun `framing adds a length prefix`() {
        val result = ascii87.build("0800", mapOf(11 to "000001"), FramingSpec(LengthPrefix.BINARY_2))
        val bytes = result.bytes!!
        assertEquals(bytes.size - 2, ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF))
    }

    @Test
    fun `field checks name the field and the problem`() {
        assertNull(ascii87.checkField(2, "4111111111111111"))
        assertEquals("Field 2 (Primary account number): numeric value must contain digits only.", ascii87.checkField(2, "4111-1111"))
        assertEquals("Field 2 (Primary account number): length 20 exceeds maximum 19.", ascii87.checkField(2, "1".repeat(20)))
        assertEquals("Field 3 (Processing code): length 7 must be exactly 6.", ascii87.checkField(3, "0000000"))
        assertEquals("Field 52 (Personal identification number data): binary value must be an even number of hex digits.",
            ascii87.checkField(52, "ABC"))
        assertEquals("Field 1 is not defined in dialect 'ISO 8583:1987 ASCII'.", ascii87.checkField(1, "x"))
    }

    @Test
    fun `character classes of a, an and ans are enforced`() {
        assertNull(ascii87.checkField(49, "504"))
        assertEquals("Field 49 (Currency code, transaction): '#' is not allowed; type an takes letters, digits and spaces.",
            ascii87.checkField(49, "5#4"))
        assertNull(ascii87.checkField(41, "TERM-01#"))
        assertTrue(ascii87.checkField(41, "TERMé01")!!.contains("printable ASCII"))
    }

    @Test
    fun `field 55 must hold well formed TLV`() {
        assertNull(ascii87.checkField(55, field55))
        assertEquals("Field 55 (ICC data (EMV)): Tag 9F02: length 6 exceeds remaining 3 bytes at offset 0x3.",
            ascii87.checkField(55, "9F0206000000"))
    }

    @Test
    fun `build reports every problem with its field`() {
        val result = ascii87.build("01A0", mapOf(2 to "abc", 3 to "1", 55 to "9F02"))
        assertFalse(result.isSuccess)
        assertEquals(listOf(null, 2, 55), result.errors.map { it.fieldId })
        assertEquals("MTI must contain digits only.", result.errors[0].message)
        assertEquals("MTI must be 4 digits.", ascii87.checkMti("100"))
    }

    @Test
    fun `format hints describe each field`() {
        val d = ascii87.dialect
        assertEquals("n 12, fixed", ascii87.formatHint(d.field(4)!!))
        assertEquals("n ..19, LLVAR", ascii87.formatHint(d.field(2)!!))
        assertEquals("b ..255 bytes as hex, LLLVAR", ascii87.formatHint(d.field(55)!!))
        assertEquals((2..128).filter { d.field(it) != null }, ascii87.fields.map { it.id }.filter { it <= 128 })
    }
}
