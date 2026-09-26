package io.github.mouadai.cardwire.core.builder

import io.github.mouadai.cardwire.core.emv.Hex
import io.github.mouadai.cardwire.core.iso.Dialect
import io.github.mouadai.cardwire.core.iso.FieldType
import io.github.mouadai.cardwire.core.iso.IsoMessageData
import java.util.Base64

/** Output formats offered by the builder (SPEC 8.3). */
enum class ExportFormat(val label: String) {
    HEX("Hex"),
    BASE64("Base64"),
    JAVA_BYTES("Java byte[]"),
    KOTLIN_BYTES("Kotlin ByteArray"),
    JPOS("jPOS ISOMsg"),
}

/**
 * Renders built messages for copying into code, tests or tools. Exports carry clear values: they
 * are the message the user built, and a masked export could not be sent or replayed.
 */
object Exporters {

    fun export(format: ExportFormat, bytes: ByteArray, data: IsoMessageData, dialect: Dialect): String = when (format) {
        ExportFormat.HEX -> hex(bytes)
        ExportFormat.BASE64 -> base64(bytes)
        ExportFormat.JAVA_BYTES -> javaByteArray(bytes)
        ExportFormat.KOTLIN_BYTES -> kotlinByteArray(bytes)
        ExportFormat.JPOS -> jposSnippet(data, dialect)
    }

    fun hex(bytes: ByteArray): String = Hex.encode(bytes)

    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /** `byte[] message = { (byte) 0x30, ... };`, 12 bytes per line. */
    fun javaByteArray(bytes: ByteArray, name: String = "message"): String =
        literal("byte[] $name = {", "};", bytes) { "(byte) 0x%02X".format(it) }

    /** `val message = byteArrayOf(0x30, 0x9F.toByte(), ...)`: values above 0x7F need `.toByte()`. */
    fun kotlinByteArray(bytes: ByteArray, name: String = "message"): String =
        literal("val $name = byteArrayOf(", ")", bytes) { if (it < 0x80) "0x%02X".format(it) else "0x%02X.toByte()".format(it) }

    private fun literal(open: String, close: String, bytes: ByteArray, item: (Int) -> String): String {
        if (bytes.isEmpty()) return open + close
        val lines = bytes.map { item(it.toInt() and 0xFF) }.chunked(12).joinToString(",\n") { "    " + it.joinToString(", ") }
        return "$open\n$lines,\n$close"
    }

    /**
     * Java code that builds the same message with jPOS. Binary fields go through `ISOUtil.hex2byte`;
     * packing it back to these exact bytes needs a jPOS packager matching the dialect.
     */
    fun jposSnippet(data: IsoMessageData, dialect: Dialect): String = buildString {
        append("// Built with Cardwire, dialect \"").append(javaString(dialect.name)).append("\".\n")
        append("// Pack with a GenericPackager that matches this dialect.\n")
        append("ISOMsg msg = new ISOMsg();\n")
        append("msg.setMTI(\"").append(javaString(data.mti)).append("\");\n")
        for ((id, value) in data.fields.toSortedMap()) {
            val spec = dialect.field(id)
            append("msg.set(").append(id).append(", ")
            if (spec?.type == FieldType.B) {
                append("ISOUtil.hex2byte(\"").append(value.uppercase()).append("\")")
            } else {
                append('"').append(javaString(value)).append('"')
            }
            append(");")
            spec?.let { append(" // ").append(it.name.replace('\n', ' ')) }
            append('\n')
        }
    }

    private fun javaString(s: String): String = buildString {
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' || c.code > 0x7E -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
}
