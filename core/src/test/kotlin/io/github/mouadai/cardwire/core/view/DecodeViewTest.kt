package io.github.mouadai.cardwire.core.view

import io.github.mouadai.cardwire.core.emv.Hex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DecodeViewTest {

    @Test
    fun `raw mode shows the input as one node`() {
        val view = DecodeView.decode(Hex.decode("9A03260925"), DecodeMode.RAW, reveal = false)
        assertEquals("Input", view.root.id)
        assertEquals(5, view.root.length)
        assertEquals(emptyList<String>(), view.problems)
    }

    @Test
    fun `EMV mode decodes tags and masks sensitive bytes`() {
        val view = DecodeView.decode(Hex.decode("5A084111111111111111"), DecodeMode.EMV_TLV, reveal = false)
        assertEquals(listOf("5A"), view.root.children.map { it.id })
        assertEquals(listOf(2..9), view.maskedRanges)
    }

    @Test
    fun `revealing clears masking`() {
        val view = DecodeView.decode(Hex.decode("5A084111111111111111"), DecodeMode.EMV_TLV, reveal = true)
        assertEquals(emptyList<IntRange>(), view.maskedRanges)
        assertEquals("4111111111111111", view.root.children.single().value)
    }

    @Test
    fun `EMV parse errors are reported as problems`() {
        val view = DecodeView.decode(Hex.decode("9A0526"), DecodeMode.EMV_TLV, reveal = false)
        assertEquals(1, view.problems.size)
        assertEquals(listOf("9A"), view.root.children.map { it.id })
    }
}
