package io.github.mouadai.octet.core.iso

/**
 * Writes dialect JSON in the layout of the bundled dialect files: top-level keys one per line and
 * one line per field, which keeps generated files readable and diffable.
 */
internal object DialectJsonWriter {

    fun write(dialect: Map<String, Any?>): String = buildString {
        append("{\n")
        val entries = dialect.entries.toList()
        entries.forEachIndexed { i, (key, value) ->
            append("  ").append(quote(key)).append(": ")
            if (key == "fields" && value is Map<*, *>) {
                append("{\n")
                val fields = value.entries.toList()
                fields.forEachIndexed { j, (number, field) ->
                    append("    ").append(quote(number.toString())).append(": ").append(compact(field))
                    append(if (j < fields.size - 1) ",\n" else "\n")
                }
                append("  }")
            } else {
                append(compact(value))
            }
            append(if (i < entries.size - 1) ",\n" else "\n")
        }
        append("}\n")
    }

    fun compact(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean, is Int, is Long -> value.toString()
        is Map<*, *> -> value.entries.joinToString(", ", "{", "}") { (k, v) -> quote(k.toString()) + ": " + compact(v) }
        is List<*> -> value.joinToString(", ", "[", "]") { compact(it) }
        else -> quote(value.toString())
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
