package io.github.mouadai.octet.core.view

import io.github.mouadai.octet.core.emv.EmvDecoder
import io.github.mouadai.octet.core.emv.Hex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmvTreeTest {

    private val decoder = EmvDecoder()

    private fun tree(hex: String, reveal: Boolean = false): DecodeNode {
        val bytes = Hex.decode(hex)
        return EmvTree.build(decoder.decode(bytes, reveal = reveal), bytes.size)
    }

    @Test
    fun `root spans the input and lists top-level tags with offsets covering tag, length and value`() {
        val root = tree("9F02060000000010005F2A020504")
        assertEquals("EMV TLV", root.id)
        assertEquals(0, root.offset)
        assertEquals(14, root.length)

        val (amount, currency) = root.children
        assertEquals("9F02", amount.id)
        assertEquals("Amount, Authorised (Numeric)", amount.name)
        assertEquals("10.00 MAD", amount.value)
        assertEquals("000000001000", amount.rawHex)
        assertEquals(0, amount.offset)
        assertEquals(9, amount.length)

        assertEquals("5F2A", currency.id)
        assertEquals(9, currency.offset)
        assertEquals(5, currency.length)
    }

    @Test
    fun `set bits become children located on their byte`() {
        // TVR with byte 1 bit 8 (offline data authentication not performed) set.
        val tvr = tree("95058000000000").children.single()
        val bit = tvr.children.single()
        assertEquals("Byte 1 bit 8", bit.id)
        assertTrue(bit.name.isNotEmpty())
        assertEquals(2, bit.offset)
        assertEquals(1, bit.length)
    }

    @Test
    fun `constructed tags nest their children`() {
        val template = tree("70079F02060000000010").children.single()
        assertEquals("70", template.id)
        val amount = template.children.single()
        assertEquals("9F02", amount.id)
        assertEquals(2, amount.offset)
        assertEquals(listOf("70", "9F02"), tree("70079F02060000000010").pathAt(5)?.drop(1)?.map { it.id })
    }

    @Test
    fun `sensitive tags are masked unless revealed, including their raw hex`() {
        val masked = tree("5A0841111111111111115F200A5445535420434152442F").children
        assertEquals("411111******1111", masked[0].value)
        assertTrue(masked[0].rawHex.contains('*'))
        assertEquals("**********", masked[1].value)

        val clear = tree("5A084111111111111111", reveal = true).children.single()
        assertEquals("4111111111111111", clear.value)
        assertEquals("4111111111111111", clear.rawHex)
    }

    @Test
    fun `masked ranges cover the value bytes of sensitive tags only`() {
        val hex = "9A032609255A084111111111111111"
        val result = decoder.decode(Hex.decode(hex))
        assertEquals(listOf(7..14), EmvTree.maskedRanges(result))
        assertEquals(emptyList<IntRange>(), EmvTree.maskedRanges(decoder.decode(Hex.decode(hex), reveal = true)))
    }

    @Test
    fun `truncated input keeps what decoded and flags the problem`() {
        val root = tree("9A032609259F0206000000")
        assertEquals(listOf("9A", "9F02"), root.children.map { it.id })
        assertTrue(root.children[1].children.any { it.id == "!" })
    }
}
