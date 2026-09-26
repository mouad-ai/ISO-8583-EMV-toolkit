package io.github.mouadai.octet.core.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HexRunsTest {

    private val sixteenBytes = "9F02060000000010005F2A0205049A03"

    private fun found(line: String) = HexRuns.find(line).map { line.substring(it) }

    @Test
    fun `finds a contiguous hex run of at least 16 bytes`() {
        assertEquals(listOf(sixteenBytes), found("DEBUG icc=$sixteenBytes done"))
    }

    @Test
    fun `finds space-separated byte pairs`() {
        val spaced = sixteenBytes.chunked(2).joinToString(" ")
        assertEquals(listOf(spaced), found("rx: $spaced"))
    }

    @Test
    fun `ignores short runs and odd-length runs`() {
        assertEquals(emptyList<String>(), found("id=9F0206000000001000 ok"))
        assertEquals(emptyList<String>(), found("x=${sixteenBytes}A"))
    }

    @Test
    fun `does not start or end inside a longer word`() {
        assertEquals(emptyList<String>(), found("0${sixteenBytes}Z"))
        assertEquals(listOf(sixteenBytes), found("[$sixteenBytes]"))
    }

    @Test
    fun `finds several runs on one line`() {
        val other = "0100722404002080820016411111111111"
        assertEquals(listOf(sixteenBytes, other), found("req=$sixteenBytes rsp=$other"))
    }
}
