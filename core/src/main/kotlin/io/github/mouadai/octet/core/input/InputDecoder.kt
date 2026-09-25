package io.github.mouadai.octet.core.input

import java.util.Base64

/** How pasted text should be turned into bytes. */
enum class InputFormat { AUTO, HEX, BASE64, ASCII }

sealed interface InputResult {
    /** [format] is the format actually used, never [InputFormat.AUTO]. */
    class Success(val bytes: ByteArray, val format: InputFormat) : InputResult {
        override fun toString(): String = "Success(format=$format, ${bytes.size} bytes)"
    }

    data class Failure(val message: String) : InputResult
}

/**
 * Converts text pasted by the user (hex dump, base64 or raw characters) into bytes.
 *
 * Auto-detection order: hex, then base64, then raw ASCII. Text that is valid as both hex and base64
 * (e.g. `AAAA`) is treated as hex, since hex dumps are by far the most common input.
 */
object InputDecoder {

    private val WHITESPACE = Regex("\\s+")
    private val HEX_NOISE = Regex("0[xX]|[\\s,]")
    private val BASE64_SHAPE = Regex("[A-Za-z0-9+/]+={0,2}")

    fun decode(text: String, format: InputFormat = InputFormat.AUTO): InputResult {
        if (text.isBlank()) return InputResult.Failure("Input is empty.")
        return when (format) {
            InputFormat.HEX -> decodeHex(text)
            InputFormat.BASE64 -> decodeBase64(text)
            InputFormat.ASCII -> ascii(text)
            InputFormat.AUTO -> sequenceOf(::decodeHex, ::decodeBase64)
                .map { it(text) }
                .firstOrNull { it is InputResult.Success }
                ?: ascii(text)
        }
    }

    private fun decodeHex(text: String): InputResult {
        val digits = text.replace(HEX_NOISE, "")
        val invalid = digits.indexOfFirst { Character.digit(it, 16) < 0 }
        if (invalid >= 0) {
            return InputResult.Failure(
                "Hex input contains '${digits[invalid]}', which is not a hex digit (at digit ${invalid + 1}).",
            )
        }
        if (digits.length % 2 != 0) {
            return InputResult.Failure("Hex input has an odd number of digits (${digits.length}).")
        }
        val bytes = ByteArray(digits.length / 2) {
            ((Character.digit(digits[2 * it], 16) shl 4) or Character.digit(digits[2 * it + 1], 16)).toByte()
        }
        return InputResult.Success(bytes, InputFormat.HEX)
    }

    private fun decodeBase64(text: String): InputResult {
        val compact = text.replace(WHITESPACE, "")
        if (compact.length % 4 != 0 || !BASE64_SHAPE.matches(compact)) {
            return InputResult.Failure("Input is not valid base64.")
        }
        return try {
            InputResult.Success(Base64.getDecoder().decode(compact), InputFormat.BASE64)
        } catch (_: IllegalArgumentException) {
            InputResult.Failure("Input is not valid base64.")
        }
    }

    /** Raw characters are taken as-is; characters above U+00FF are encoded as UTF-8. */
    private fun ascii(text: String): InputResult {
        val latin1 = Charsets.ISO_8859_1.newEncoder()
        val bytes = if (latin1.canEncode(text)) text.toByteArray(Charsets.ISO_8859_1) else text.toByteArray(Charsets.UTF_8)
        return InputResult.Success(bytes, InputFormat.ASCII)
    }
}
