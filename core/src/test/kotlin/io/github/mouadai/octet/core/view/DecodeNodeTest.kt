package io.github.mouadai.octet.core.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DecodeNodeTest {

    private val amount = DecodeNode("9F02", "Amount, Authorised", "10.00", offset = 0, length = 9)
    private val currency = DecodeNode("5F2A", "Transaction Currency Code", "504", offset = 9, length = 5)
    private val amountValue = DecodeNode("value", "", "000000001000", offset = 3, length = 6)
    private val root = DecodeNode(
        "TLV", "EMV data", "", offset = 0, length = 14,
        children = listOf(amount.copy(children = listOf(amountValue)), currency),
    )

    @Test
    fun `path to the deepest node containing an offset`() {
        assertEquals(listOf("TLV", "9F02", "value"), root.pathAt(4)?.map { it.id })
        assertEquals(listOf("TLV", "9F02"), root.pathAt(1)?.map { it.id })
        assertEquals(listOf("TLV", "5F2A"), root.pathAt(13)?.map { it.id })
    }

    @Test
    fun `offsets outside the node have no path`() {
        assertNull(root.pathAt(14))
        assertNull(root.pathAt(-1))
    }

    @Test
    fun `zero-length nodes never match`() {
        val empty = DecodeNode("x", "", "", offset = 0, length = 0)
        assertNull(empty.pathAt(0))
    }
}
