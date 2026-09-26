package io.github.mouadai.octet.core.builder

import io.github.mouadai.octet.core.emv.Hex
import io.github.mouadai.octet.core.iso.BuiltinDialects
import io.github.mouadai.octet.core.iso.IsoMessageData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExportersTest {
    private val bytes = Hex.decode("30 9F 00 FF")

    @Test
    fun `hex and base64`() {
        assertEquals("309F00FF", Exporters.hex(bytes))
        assertEquals("MJ8A/w==", Exporters.base64(bytes))
    }

    @Test
    fun `java byte array literal`() {
        assertEquals(
            "byte[] message = {\n    (byte) 0x30, (byte) 0x9F, (byte) 0x00, (byte) 0xFF,\n};",
            Exporters.javaByteArray(bytes),
        )
        assertEquals("byte[] message = {};", Exporters.javaByteArray(ByteArray(0)))
    }

    @Test
    fun `kotlin byte array literal wraps every 12 bytes`() {
        assertEquals(
            "val message = byteArrayOf(\n    0x30, 0x9F.toByte(), 0x00, 0xFF.toByte(),\n)",
            Exporters.kotlinByteArray(bytes),
        )
        val lines = Exporters.kotlinByteArray(ByteArray(13)).lines()
        assertEquals(4, lines.size)
        assertEquals(12, lines[1].split(", ").size)
    }

    @Test
    fun `jPOS snippet uses strings for text fields and hex2byte for binary ones`() {
        val dialect = BuiltinDialects.load("iso8583-1987-ascii")
        val data = IsoMessageData("0200", mapOf(4 to "000000001000", 41 to "TERM\"01\\", 55 to "9a03260925"))
        assertEquals(
            """
            // Built with Octet, dialect "ISO 8583:1987 ASCII".
            // Pack with a GenericPackager that matches this dialect.
            ISOMsg msg = new ISOMsg();
            msg.setMTI("0200");
            msg.set(4, "000000001000"); // Amount, transaction
            msg.set(41, "TERM\"01\\"); // Card acceptor terminal identification
            msg.set(55, ISOUtil.hex2byte("9A03260925")); // ICC data (EMV)

            """.trimIndent(),
            Exporters.jposSnippet(data, dialect),
        )
    }

    @Test
    fun `export dispatches on format`() {
        val dialect = BuiltinDialects.load("iso8583-1987-ascii")
        val data = IsoMessageData("0800", emptyMap())
        assertEquals("309F00FF", Exporters.export(ExportFormat.HEX, bytes, data, dialect))
        assertEquals(ExportFormat.entries.size, ExportFormat.entries.map { Exporters.export(it, bytes, data, dialect) }.toSet().size)
    }
}
