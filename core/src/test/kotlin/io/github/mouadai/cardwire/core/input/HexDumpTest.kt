package io.github.mouadai.cardwire.core.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HexDumpTest {

    private fun bytes(count: Int, start: Int = 0x41) = ByteArray(count) { (start + it).toByte() }

    @Test
    fun `renders offset, hex and ascii columns`() {
        val dump = HexDump(byteArrayOf(0x30, 0x31, 0x00, 0x7F), bytesPerLine = 4)
        assertEquals("0000  30 31 00 7F  01..", dump.text)
    }

    @Test
    fun `pads the hex column of a short last line`() {
        val dump = HexDump(bytes(5), bytesPerLine = 4)
        assertEquals(
            "0000  41 42 43 44  ABCD\n" +
                "0004  45" + " ".repeat(11) + "E",
            dump.text,
        )
    }

    @Test
    fun `uses a wider offset column for large inputs`() {
        val dump = HexDump(ByteArray(0x10001), bytesPerLine = 16)
        assertEquals("00000000  00", dump.text.lineSequence().first().take(12))
    }

    @Test
    fun `empty input renders nothing`() {
        assertEquals("", HexDump(ByteArray(0)).text)
    }

    @Test
    fun `highlight ranges cover hex and ascii columns on one line`() {
        val dump = HexDump(bytes(4), bytesPerLine = 4)
        // "0000  41 42 43 44  ABCD": bytes 1..2 are "42 43" at 9..13 and "BC" at 20..21
        assertEquals(listOf(9..13, 20..21), dump.highlightRanges(offset = 1, length = 2))
    }

    @Test
    fun `highlight ranges split across lines`() {
        val dump = HexDump(bytes(6), bytesPerLine = 4)
        val secondLine = "0000  41 42 43 44  ABCD\n".length
        assertEquals(
            listOf(15..16, 22..22, secondLine + 6..secondLine + 10, secondLine + 19..secondLine + 20),
            dump.highlightRanges(offset = 3, length = 3),
        )
    }

    @Test
    fun `highlight ranges are clamped to the data`() {
        val dump = HexDump(bytes(4), bytesPerLine = 4)
        assertEquals(listOf(15..16, 22..22), dump.highlightRanges(offset = 3, length = 10))
        assertEquals(emptyList<IntRange>(), dump.highlightRanges(offset = 9, length = 1))
        assertEquals(emptyList<IntRange>(), dump.highlightRanges(offset = 0, length = 0))
    }

    @Test
    fun `maps text positions back to byte offsets`() {
        val dump = HexDump(bytes(6), bytesPerLine = 4)
        assertEquals(0, dump.byteOffsetAt(6))
        assertEquals(0, dump.byteOffsetAt(7))
        assertEquals(1, dump.byteOffsetAt(9))
        assertEquals(2, dump.byteOffsetAt(21)) // ASCII column
        val secondLine = "0000  41 42 43 44  ABCD\n".length
        assertEquals(5, dump.byteOffsetAt(secondLine + 9))
        assertEquals(5, dump.byteOffsetAt(secondLine + 20))
    }

    @Test
    fun `positions outside any byte map to null`() {
        val dump = HexDump(bytes(6), bytesPerLine = 4)
        assertNull(dump.byteOffsetAt(0)) // offset column
        assertNull(dump.byteOffsetAt(8)) // space between bytes
        val secondLine = "0000  41 42 43 44  ABCD\n".length
        assertNull(dump.byteOffsetAt(secondLine + 12)) // hex padding past the last byte
        assertNull(dump.byteOffsetAt(10_000))
    }

    @Test
    fun `masked bytes are hidden in both columns`() {
        val dump = HexDump(bytes(4), bytesPerLine = 4, masked = listOf(1..2))
        assertEquals("0000  41 ** ** 44  A**D", dump.text)
        assertEquals(listOf(9..13, 20..21), dump.highlightRanges(offset = 1, length = 2))
    }
}
