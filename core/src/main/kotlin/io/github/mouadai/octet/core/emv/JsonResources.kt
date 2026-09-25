package io.github.mouadai.octet.core.emv

/**
 * Loads the bundled JSON data files. `core` has no third-party dependencies, so this includes a
 * small strict JSON reader: objects become [Map], arrays [List], numbers [Long] or [Double].
 */
internal object JsonResources {
    fun load(path: String): Any? {
        val stream = JsonResources::class.java.getResourceAsStream(path)
            ?: throw IllegalStateException("Missing bundled resource $path")
        return stream.bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
    }

    fun parse(text: String): Any? = Reader(text).readDocument()

    private class Reader(private val s: String) {
        private var i = 0

        fun readDocument(): Any? {
            val v = readValue()
            skipWs()
            if (i != s.length) fail("Unexpected trailing content")
            return v
        }

        private fun fail(msg: String): Nothing = throw IllegalArgumentException("$msg at JSON position $i")

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun expect(c: Char) {
            skipWs()
            if (i >= s.length || s[i] != c) fail("Expected '$c'")
            i++
        }

        private fun readValue(): Any? {
            skipWs()
            if (i >= s.length) fail("Unexpected end of input")
            return when (val c = s[i]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else fail("Unexpected '$c'")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) fail("Expected $word")
            i += word.length
            return value
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                skipWs()
                if (i >= s.length || s[i] != '"') fail("Expected object key")
                val key = readString()
                expect(':')
                out[key] = readValue()
                skipWs()
                if (i < s.length && s[i] == ',') { i++; continue }
                expect('}')
                return out
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                out += readValue()
                skipWs()
                if (i < s.length && s[i] == ',') { i++; continue }
                expect(']')
                return out
            }
        }

        private fun readString(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("Unterminated escape")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("Bad unicode escape")
                                sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("Bad unicode escape"))
                                i += 4
                            }
                            else -> fail("Bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val text = s.substring(start, i)
            return text.toLongOrNull() ?: text.toDoubleOrNull() ?: fail("Bad number '$text'")
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.asObject(what: String): Map<String, Any?> =
    this as? Map<String, Any?> ?: throw IllegalArgumentException("Expected a JSON object for $what")

internal fun Any?.asArray(what: String): List<Any?> =
    this as? List<Any?> ?: throw IllegalArgumentException("Expected a JSON array for $what")

internal fun Map<String, Any?>.string(key: String): String? = when (val v = this[key]) {
    null -> null
    is String -> v
    is Long -> v.toString()
    else -> throw IllegalArgumentException("Expected a string for '$key' in $this")
}

internal fun Map<String, Any?>.requireString(key: String): String =
    string(key) ?: throw IllegalArgumentException("Missing '$key' in $this")
