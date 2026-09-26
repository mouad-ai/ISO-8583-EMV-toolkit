package io.github.mouadai.octet.core.builder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TlvBuilderTest {
    private val encoder = EmvValueEncoder()

    private fun ok(r: EmvValueEncoder.Result) = (r as EmvValueEncoder.Result.Ok).hex
    private fun err(r: EmvValueEncoder.Result) = (r as EmvValueEncoder.Result.Error).message

    @Test
    fun `builds field 55 from rows`() {
        val r = TlvBuilder.build(listOf(
            TlvEntry("9F02", "000000001000"),
            TlvEntry("5F2A", "0504"),
            TlvEntry("9A", "260925"),
            TlvEntry("95", "0000000000"),
        ))
        assertEquals("9F02060000000010005F2A0205049A0326092595050000000000", r.hex)
    }

    @Test
    fun `long values get the long length form`() {
        val r = TlvBuilder.build(listOf(TlvEntry("9F10", "AB".repeat(130))))
        assertEquals("9F108182" + "AB".repeat(130), r.hex)
    }

    @Test
    fun `constructed tags take TLV as their value`() {
        assertEquals("70059F36020001", TlvBuilder.build(listOf(TlvEntry("70", "9F36020001"))).hex)
        assertEquals(
            listOf(TlvEntryError(0, "Tag 70 is constructed, so its value must be TLV: Tag 9F36: length 2 exceeds remaining 1 bytes at offset 0x3.")),
            TlvBuilder.build(listOf(TlvEntry("70", "9F360200"))).errors,
        )
    }

    @Test
    fun `bad rows are reported by index`() {
        val r = TlvBuilder.build(listOf(TlvEntry("9A", "260925"), TlvEntry("9F", "00"), TlvEntry("82", "XYZ")))
        assertNull(r.hex)
        assertEquals(listOf(1, 2), r.errors.map { it.index })
        assertEquals("Tag 9F is incomplete.", r.errors[0].message)
    }

    @Test
    fun `tag checks`() {
        assertNull(TlvBuilder.checkTag("9F02"))
        assertNull(TlvBuilder.checkTag("df8101"))
        assertEquals("Tag 9A02 has extra bytes (a valid tag would be 9A).", TlvBuilder.checkTag("9A02"))
        assertEquals("Tag 'G1' is not hex.", TlvBuilder.checkTag("G1"))
        assertEquals("Tag is empty.", TlvBuilder.checkTag(""))
    }

    @Test
    fun `rows round trip existing TLV`() {
        val hex = "9F02060000000010005F2A020504"
        val rows = TlvBuilder.rows(hex)!!
        assertEquals(listOf(TlvEntry("9F02", "000000001000"), TlvEntry("5F2A", "0504")), rows)
        assertEquals(hex, TlvBuilder.build(rows).hex)
        assertNull(TlvBuilder.rows("9F0206"))
        assertNull(TlvBuilder.rows("zz"))
    }

    @Test
    fun `friendly values are encoded per tag format`() {
        assertEquals("000000001000", ok(encoder.encode("9F02", "10.00")))
        assertEquals("000000001000", ok(encoder.encode("9F02", "10", currency = "MAD")))
        assertEquals("000000000010", ok(encoder.encode("9F02", "10", currency = "JPY")))
        assertEquals("000000010000", ok(encoder.encode("9F03", "10.000", currency = "414")))
        assertEquals("0504", ok(encoder.encode("5F2A", "MAD")))
        assertEquals("0504", ok(encoder.encode("5F2A", "504")))
        assertEquals("0504", ok(encoder.encode("9F1A", "MA")))
        assertEquals("0840", ok(encoder.encode("9F1A", "USA")))
        assertEquals("260925", ok(encoder.encode("9A", "2026-09-25")))
        assertEquals("260925", ok(encoder.encode("9A", "260925")))
        assertEquals("134501", ok(encoder.encode("9F21", "13:45:01")))
        assertEquals("00", ok(encoder.encode("9C", "0")))
        assertEquals("0201", ok(encoder.encode("5F30", "201")))
        assertEquals("3030", ok(encoder.encode("8A", "00")))
        assertEquals("4111111111111111", ok(encoder.encode("5A", "4111111111111111")))
        assertEquals("476173900101011", ok(encoder.encode("5A", "476173900101011")).dropLast(1))
        assertEquals("80", ok(encoder.encode("9F27", "80")))
        assertEquals("CAFE", ok(encoder.encode("DF01", "ca fe")))
    }

    @Test
    fun `friendly values are checked`() {
        assertEquals("Amount has more than 2 decimal places.", err(encoder.encode("9F02", "10.001")))
        assertEquals("Amount must look like 10.00.", err(encoder.encode("9F02", "ten")))
        assertEquals("Amount does not fit in 12 digits.", err(encoder.encode("9F02", "99999999999.00")))
        assertEquals("'2026-13-01' is not a valid date.", err(encoder.encode("9A", "2026-13-01")))
        assertEquals("EMV dates cover years 1950 to 2049.", err(encoder.encode("9A", "2050-01-01")))
        assertEquals("Unknown currency 'XXX1'.", err(encoder.encode("5F2A", "XXX1")))
        assertEquals("At most 2 digits.", err(encoder.encode("9C", "123")))
        assertEquals("Odd number of hex digits (3).", err(encoder.encode("9F26", "ABC")))
    }

    @Test
    fun `input hints`() {
        assertEquals("amount, e.g. 10.00", encoder.inputHint("9F02"))
        assertEquals("date, YYYY-MM-DD or YYMMDD", encoder.inputHint("9A"))
        assertEquals("hex", encoder.inputHint("9F26"))
        assertEquals("hex", encoder.inputHint("DF01"))
    }

    @Test
    fun `hex prefix forces raw hex for any tag`() {
        assertEquals("000000001000", ok(encoder.encode("9F02", "hex:000000001000")))
        assertEquals("FFFF", ok(encoder.encode("9A", "HEX: FF FF")))
    }

    @Test
    fun `existing values turn into editor input and back`() {
        val cases = listOf(
            Triple("9F02", "000000001000", "10.00"),
            Triple("5F2A", "0504", "MAD"),
            Triple("9F1A", "0504", "MA"),
            Triple("9A", "260925", "2026-09-25"),
            Triple("9F21", "134501", "13:45:01"),
            Triple("9C", "00", "00"),
            Triple("5F30", "0201", "201"),
            Triple("5A", "4761739001010119", "4761739001010119"),
            Triple("8A", "3030", "00"),
            Triple("9F26", "1122334455667788", "1122334455667788"),
            Triple("9A", "261399", "hex:261399"),
            Triple("50", "0001", "hex:0001"),
            Triple("9F02", "00000000100A", "hex:00000000100A"),
        )
        for ((tag, hex, input) in cases) {
            assertEquals(input, encoder.toInput(tag, hex), tag)
            assertEquals(hex, ok(encoder.encode(tag, input)), tag)
        }
        assertEquals("10", encoder.toInput("9F02", "000000000010", currency = "JPY"))
    }
}
