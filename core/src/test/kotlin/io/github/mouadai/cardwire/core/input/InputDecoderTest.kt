package io.github.mouadai.cardwire.core.input

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputDecoderTest {

    private fun success(text: String, format: InputFormat = InputFormat.AUTO): InputResult.Success {
        val result = InputDecoder.decode(text, format)
        assertTrue(result is InputResult.Success, "Expected success but got $result")
        return result as InputResult.Success
    }

    private fun failure(text: String, format: InputFormat = InputFormat.AUTO): InputResult.Failure {
        val result = InputDecoder.decode(text, format)
        assertTrue(result is InputResult.Failure, "Expected failure but got $result")
        return result as InputResult.Failure
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `plain hex is detected`() {
        val result = success("9F02060000000010005F2A020504")
        assertEquals(InputFormat.HEX, result.format)
        assertArrayEquals(
            bytes(0x9F, 0x02, 0x06, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x5F, 0x2A, 0x02, 0x05, 0x04),
            result.bytes,
        )
    }

    @Test
    fun `hex with spaces, newlines, lowercase and 0x prefixes is accepted`() {
        assertArrayEquals(bytes(0x9A, 0x03, 0x26, 0x09, 0x25), success("9a 03\n26\t09 25").bytes)
        assertArrayEquals(bytes(0x9A, 0x03, 0x26), success("0x9A 0x03 0x26").bytes)
        assertArrayEquals(bytes(0x9A, 0x03, 0x26), success("0x9A0326").bytes)
    }

    @Test
    fun `byte array literal style is accepted as hex`() {
        val result = success("0x95, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00")
        assertEquals(InputFormat.HEX, result.format)
        assertArrayEquals(bytes(0x95, 0x05, 0, 0, 0, 0, 0), result.bytes)
    }

    @Test
    fun `strings valid as both hex and base64 prefer hex`() {
        assertEquals(InputFormat.HEX, success("AAAA").format)
    }

    @Test
    fun `base64 is detected when the text is not hex`() {
        // Base64 of 9F 02 06 00 00 00 00 10 00
        val result = success("nwIGAAAAABAA")
        assertEquals(InputFormat.BASE64, result.format)
        assertArrayEquals(bytes(0x9F, 0x02, 0x06, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00), result.bytes)
    }

    @Test
    fun `base64 with padding and line breaks is accepted`() {
        val result = success("lQUA\nAAAAAA==")
        assertEquals(InputFormat.BASE64, result.format)
        assertArrayEquals(bytes(0x95, 0x05, 0, 0, 0, 0, 0), result.bytes)
    }

    @Test
    fun `anything else falls back to raw ASCII, unmodified`() {
        val result = success("0100 hello")
        assertEquals(InputFormat.ASCII, result.format)
        assertArrayEquals("0100 hello".toByteArray(Charsets.US_ASCII), result.bytes)
    }

    @Test
    fun `odd-length hex is not treated as hex in auto mode`() {
        assertEquals(InputFormat.ASCII, success("9F0").format)
    }

    @Test
    fun `forced ASCII keeps hex-looking text as characters`() {
        val result = success("0100", InputFormat.ASCII)
        assertEquals(InputFormat.ASCII, result.format)
        assertArrayEquals(bytes(0x30, 0x31, 0x30, 0x30), result.bytes)
    }

    @Test
    fun `forced hex reports an odd digit count`() {
        assertEquals("Hex input has an odd number of digits (3).", failure("9F0", InputFormat.HEX).message)
    }

    @Test
    fun `forced hex reports the first invalid character`() {
        assertEquals(
            "Hex input contains 'G', which is not a hex digit (at digit 3).",
            failure("9FG0", InputFormat.HEX).message,
        )
    }

    @Test
    fun `forced base64 reports invalid input`() {
        assertEquals("Input is not valid base64.", failure("n!IG", InputFormat.BASE64).message)
    }

    @Test
    fun `empty or blank input is a failure`() {
        assertEquals("Input is empty.", failure("").message)
        assertEquals("Input is empty.", failure("  \n ").message)
    }
}
