package io.github.mouadai.cardwire.core.emv

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BerTlvTest {
    // Hand-built field 55 sample (SPEC section 11 values plus a few common tags). No real card data.
    private val sample =
        "9F02 06 000000001000" +
        "5F2A 02 0504" +
        "9A 03 260925" +
        "95 05 0000000000" +
        "82 02 1980" +
        "9F26 08 1122334455667788" +
        "9F27 01 80" +
        "9F36 02 0001"

    @Test
    fun `decodes single and multi byte tags with offsets`() {
        val r = BerTlv.decode(sample)
        assertTrue(r.isComplete, r.errors.toString())
        assertEquals(listOf("9F02", "5F2A", "9A", "95", "82", "9F26", "9F27", "9F36"), r.nodes.map { it.tag.hex })

        val amount = r.nodes[0]
        assertEquals(0, amount.offset)
        assertEquals(3, amount.headerLength)
        assertEquals(3, amount.valueOffset)
        assertEquals("000000001000", amount.valueHex)

        val currency = r.nodes[1]
        assertEquals(9, currency.offset)
        assertEquals("0504", currency.valueHex)
        assertEquals("260925", r.nodes[2].valueHex)
    }

    @Test
    fun `offsets are shifted by baseOffset`() {
        val r = BerTlv.decode(Hex.decode("9A03260925"), baseOffset = 0x40)
        assertEquals(0x40, r.nodes.single().offset)
        assertEquals(0x42, r.nodes.single().valueOffset)
    }

    @Test
    fun `recurses into constructed tags`() {
        // 70 { 5F20 "TEST/CARD", 77 { 9F36 0001 } }
        val r = BerTlv.decode("70 13 5F20 09 544553542F43415244 77 05 9F36 02 0001")
        assertTrue(r.isComplete, r.errors.toString())
        val template = r.nodes.single()
        assertTrue(template.tag.isConstructed)
        assertEquals(listOf("5F20", "77"), template.children.map { it.tag.hex })
        val inner = template.children[1].children.single()
        assertEquals("9F36", inner.tag.hex)
        assertEquals(16, inner.offset)
        assertNotNull(r.find("9F36"))
    }

    @Test
    fun `three byte tag`() {
        val r = BerTlv.decode("DF 81 01 01 AA")
        assertEquals("DF8101", r.nodes.single().tag.hex)
        assertEquals(TagClass.PRIVATE, r.nodes.single().tag.tagClass)
        assertEquals("AA", r.nodes.single().valueHex)
    }

    @Test
    fun `long form lengths`() {
        val v129 = "AB".repeat(129)
        val r = BerTlv.decode("9F10 81 81 $v129")
        assertTrue(r.isComplete)
        assertEquals(129, r.nodes.single().length)
        assertEquals(4, r.nodes.single().headerLength)

        val v300 = "CD".repeat(300)
        val r2 = BerTlv.decode("90 82 012C $v300")
        assertEquals(300, r2.nodes.single().length)
    }

    @Test
    fun `round trip is byte exact, including non-minimal length forms`() {
        val inputs = listOf(
            sample,
            "70 13 5F20 09 544553542F43415244 77 05 9F36 02 0001",
            "9F02 81 06 000000001000",          // non-minimal long form kept as is
            "90 82 0003 010203",
            "9F10 81 81 " + "AB".repeat(129),
            "DF 81 01 00",
        )
        for (hex in inputs) {
            val bytes = Hex.decode(hex)
            val r = BerTlv.decode(bytes)
            assertTrue(r.isComplete, "$hex: ${r.errors}")
            assertArrayEquals(bytes, BerTlv.encode(r.nodes), hex)
        }
    }

    @Test
    fun `encoder builds minimal lengths`() {
        val node = TlvNode.constructed(TlvTag.of("70"), listOf(
            TlvNode.primitive("5A", "4761739001010119"),
            TlvNode.primitive("9F10", "00".repeat(200)),
        ))
        val bytes = BerTlv.encode(node)
        assertEquals("70", Hex.encode(bytes, 0, 1))
        // 5A: 1 + 1 + 8 = 10 bytes; 9F10: 2 + 2 (81 C8) + 200 = 204 bytes; 214 = 0xD6 needs the 81 form.
        assertEquals("81D6", Hex.encode(bytes, 1, 3))
        assertEquals("9F1081C8", Hex.encode(bytes, 13, 17))
        assertEquals(217, bytes.size)
    }

    @Test
    fun `unknown tags decode structurally`() {
        val r = BerTlv.decode("9F7F 02 CAFE DF01 01 00")
        assertTrue(r.isComplete)
        assertEquals(listOf("9F7F", "DF01"), r.nodes.map { it.tag.hex })
    }

    @Test
    fun `zero padding between objects is skipped`() {
        val r = BerTlv.decode("00 9A 03 260925 00 00 9C 01 00 00")
        assertTrue(r.isComplete)
        assertEquals(listOf("9A", "9C"), r.nodes.map { it.tag.hex })
    }

    @Test
    fun `length past end gives partial result and error with offset`() {
        // 9A decodes, then 9F02 claims 6 bytes but only 3 remain.
        val r = BerTlv.decode("9A 03 260925 9F02 06 000000")
        assertEquals(1, r.errors.size)
        val error = r.errors.single()
        assertEquals(8, error.offset)
        assertEquals("Tag 9F02: length 6 exceeds remaining 3 bytes at offset 0x8.", error.message)
        assertEquals(listOf("9A", "9F02"), r.nodes.map { it.tag.hex })
        val truncated = r.nodes[1]
        assertTrue(truncated.isTruncated)
        assertEquals(6, truncated.declaredLength)
        assertEquals(3, truncated.length)
        // Even a truncated parse re-encodes to the original bytes.
        assertArrayEquals(Hex.decode("9A 03 260925 9F02 06 000000"), BerTlv.encode(r.nodes))
    }

    @Test
    fun `truncated multi byte tag`() {
        val r = BerTlv.decode("9A 03 260925 9F")
        assertEquals(1, r.nodes.size)
        assertEquals("Tag 9F is truncated at offset 0x6.", r.errors.single().message)
    }

    @Test
    fun `missing length`() {
        val r = BerTlv.decode("9F02")
        assertTrue(r.nodes.isEmpty())
        assertEquals("Tag 9F02: missing length at offset 0x2.", r.errors.single().message)
    }

    @Test
    fun `indefinite and oversized length forms are rejected`() {
        assertEquals("Tag 70: indefinite length (0x80) is not allowed at offset 0x1.",
            BerTlv.decode("70 80 9A 03 260925 00 00").errors.single().message)
        assertEquals("Tag 9F10: length uses 5 bytes (max 4) at offset 0x2.",
            BerTlv.decode("9F10 85 0000000001 AA").errors.single().message)
        assertEquals("Tag 90: length needs 2 more bytes but only 1 remain at offset 0x2.",
            BerTlv.decode("90 82 01").errors.single().message)
    }

    @Test
    fun `errors inside a constructed tag keep earlier siblings`() {
        // 77 declares 9 bytes: 9F36 02 0001 then 9F26 08 with only 1 byte of value left.
        val r = BerTlv.decode("9C 01 00 77 09 9F36 02 0001 9F26 08 11")
        assertEquals(listOf("9C", "77"), r.nodes.map { it.tag.hex })
        assertEquals(listOf("9F36", "9F26"), r.nodes[1].children.map { it.tag.hex })
        assertEquals("Tag 9F26: length 8 exceeds remaining 1 bytes at offset 0xD.", r.errors.single().message)
    }

    @Test
    fun `deep nesting stops with an error instead of overflowing`() {
        var hex = "9A03260925"
        repeat(40) { hex = "70" + Hex.encode(BerTlv.encodeLength(hex.length / 2)) + hex }
        val r = BerTlv.decode(hex)
        assertFalse(r.isComplete)
        assertTrue(r.errors.single().message.contains("nesting deeper than 32"))
    }

    @Test
    fun `hex input is lenient about spacing and prefixes`() {
        assertArrayEquals(byteArrayOf(0x9F.toByte(), 0x02), Hex.decode("0x9f 02"))
        assertArrayEquals(byteArrayOf(0x9F.toByte(), 0x02), Hex.decode("9F:02\n"))
    }
}
