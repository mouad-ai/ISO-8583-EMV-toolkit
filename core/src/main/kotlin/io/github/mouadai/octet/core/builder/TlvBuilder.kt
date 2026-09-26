package io.github.mouadai.octet.core.builder

import io.github.mouadai.octet.core.emv.BerTlv
import io.github.mouadai.octet.core.emv.DisplayKind
import io.github.mouadai.octet.core.emv.EmvTagDictionary
import io.github.mouadai.octet.core.emv.Hex
import io.github.mouadai.octet.core.emv.ReferenceTables
import io.github.mouadai.octet.core.emv.TagInfo
import io.github.mouadai.octet.core.emv.TlvNode
import io.github.mouadai.octet.core.emv.TlvTag

/** One row of the field 55 editor: a tag and its value as hex. */
data class TlvEntry(val tag: String, val valueHex: String)

/** A problem with one row of the TLV editor ([index] is 0-based). */
data class TlvEntryError(val index: Int, val message: String)

class TlvBuildResult(val hex: String?, val errors: List<TlvEntryError>) {
    val isSuccess: Boolean get() = hex != null
}

/** Builds EMV BER-TLV data (field 55) from editor rows, and turns existing TLV back into rows. */
object TlvBuilder {

    /** Checks a tag typed as hex; null when it is exactly one well-formed BER tag. */
    fun checkTag(tag: String): String? {
        val bytes = try {
            Hex.decode(tag)
        } catch (e: IllegalArgumentException) {
            return "Tag '$tag' is not hex."
        }
        if (bytes.isEmpty()) return "Tag is empty."
        val multi = bytes[0].toInt() and 0x1F == 0x1F
        val expected = if (!multi) 1 else {
            var n = 1
            while (n < bytes.size && bytes[n].toInt() and 0x80 != 0) n++
            n + 1
        }
        if (bytes.size != expected) {
            return if (bytes.size < expected) "Tag ${Hex.encode(bytes)} is incomplete." else "Tag ${Hex.encode(bytes)} has extra bytes (a valid tag would be ${Hex.encode(bytes, 0, expected)})."
        }
        return null
    }

    fun build(entries: List<TlvEntry>): TlvBuildResult {
        val errors = mutableListOf<TlvEntryError>()
        val nodes = entries.mapIndexedNotNull { i, e ->
            checkTag(e.tag)?.let { errors += TlvEntryError(i, it); return@mapIndexedNotNull null }
            val tag = TlvTag.of(e.tag)
            val value = try {
                Hex.decode(e.valueHex)
            } catch (ex: IllegalArgumentException) {
                errors += TlvEntryError(i, "Tag ${tag.hex}: value is not hex (${ex.message}).")
                return@mapIndexedNotNull null
            }
            if (tag.isConstructed) {
                val inner = BerTlv.decode(value)
                inner.errors.firstOrNull()?.let {
                    errors += TlvEntryError(i, "Tag ${tag.hex} is constructed, so its value must be TLV: ${it.message}")
                    return@mapIndexedNotNull null
                }
                TlvNode.constructed(tag, inner.nodes)
            } else {
                TlvNode.primitive(tag, value)
            }
        }
        if (errors.isNotEmpty()) return TlvBuildResult(null, errors)
        return TlvBuildResult(Hex.encode(BerTlv.encode(nodes)), emptyList())
    }

    /** Top-level rows of existing TLV data, for editing a decoded field 55. Null if it does not parse. */
    fun rows(hex: String): List<TlvEntry>? {
        val parsed = try {
            BerTlv.decode(hex)
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (!parsed.isComplete) return null
        return parsed.nodes.map { TlvEntry(it.tag.hex, it.valueHex) }
    }
}

/**
 * Converts what a user types for an EMV tag into its value bytes, using the tag's dictionary format:
 * `10.00` for amounts, `2026-09-25` for dates, `MAD` or `504` for currencies, plain text for
 * `an`/`ans` tags. Tags without a friendlier form take hex, and any tag takes raw hex when the
 * input starts with `hex:`.
 */
class EmvValueEncoder(
    private val dictionary: EmvTagDictionary = EmvTagDictionary.default,
    private val tables: ReferenceTables = ReferenceTables.default,
) {
    sealed interface Result {
        data class Ok(val hex: String) : Result
        data class Error(val message: String) : Result
    }

    /** What the editor should show as the expected input for [tag]. */
    fun inputHint(tag: String): String {
        val info = dictionary[tag] ?: return "hex"
        return when (info.display) {
            DisplayKind.AMOUNT -> "amount, e.g. 10.00"
            DisplayKind.DATE -> "date, YYYY-MM-DD or YYMMDD"
            DisplayKind.TIME -> "time, HH:MM:SS"
            DisplayKind.CURRENCY -> "currency, e.g. MAD or 504"
            DisplayKind.COUNTRY -> "country, e.g. MA or 504"
            DisplayKind.NUMERIC -> "digits"
            DisplayKind.CN -> "digits"
            DisplayKind.TEXT -> "text"
            else -> "hex"
        }
    }

    /**
     * @param exponent decimal places for amounts; defaults to 2 or the currency's minor unit when
     * [currency] (numeric or alphabetic) is given.
     */
    fun encode(tag: String, input: String, currency: String? = null, exponent: Int? = null): Result {
        val info = dictionary[tag]
        val text = input.trim()
        if (text.startsWith(RAW_PREFIX, ignoreCase = true)) return hex(text.substring(RAW_PREFIX.length))
        return when (info?.display) {
            DisplayKind.AMOUNT -> amount(info, text, exponent ?: currency?.let { currencyOf(it) }?.exponent ?: 2)
            DisplayKind.DATE -> date(text)
            DisplayKind.TIME -> time(text)
            DisplayKind.CURRENCY -> currencyOf(text)?.let { Result.Ok("0" + it.numeric) }
                ?: Result.Error("Unknown currency '$text'.")
            DisplayKind.COUNTRY -> country(text)
            DisplayKind.NUMERIC -> numeric(info, text, leftPad = true)
            DisplayKind.CN -> numeric(info, text, leftPad = false)
            DisplayKind.TEXT -> if (text.all { it in ' '..'~' }) Result.Ok(Hex.encode(input.toByteArray(Charsets.US_ASCII)))
            else Result.Error("Only printable ASCII characters are allowed.")
            else -> hex(text)
        }
    }

    /**
     * The inverse of [encode]: what the editor shows for an existing value, so that
     * `encode(tag, toInput(tag, hex)) == hex`. Values that do not fit the tag's format come back as
     * `hex:...`.
     */
    fun toInput(tag: String, valueHex: String, currency: String? = null, exponent: Int? = null): String {
        val info = dictionary[tag]
        val hex = valueHex.uppercase()
        val raw = RAW_PREFIX + hex
        val digits = hex.all(Char::isDigit) && hex.isNotEmpty()
        val friendly: String? = when (info?.display) {
            DisplayKind.AMOUNT -> if (!digits || fixedDigits(info)?.let { it != hex.length } == true) null else {
                val exp = exponent ?: currency?.let { currencyOf(it) }?.exponent ?: 2
                val minor = hex.trimStart('0').ifEmpty { "0" }.padStart(exp + 1, '0')
                if (exp == 0) minor else minor.dropLast(exp) + "." + minor.takeLast(exp)
            }
            DisplayKind.DATE -> if (hex.length == 6 && digits) {
                val yy = hex.substring(0, 2).toInt()
                "%04d-%s-%s".format(if (yy < 50) 2000 + yy else 1900 + yy, hex.substring(2, 4), hex.substring(4))
            } else null
            DisplayKind.TIME -> if (hex.length == 6 && digits) "${hex.substring(0, 2)}:${hex.substring(2, 4)}:${hex.substring(4)}" else null
            DisplayKind.CURRENCY -> if (digits) tables.currency(hex)?.alpha else null
            DisplayKind.COUNTRY -> if (digits) tables.country(hex)?.alpha2 else null
            DisplayKind.NUMERIC -> if (digits) fixedDigits(info)?.let { n -> hex.takeLast(n).takeIf { hex.dropLast(n).all { it == '0' } } } ?: hex else null
            DisplayKind.CN -> hex.trimEnd('F').takeIf { t -> t.isNotEmpty() && t.all(Char::isDigit) }
            DisplayKind.TEXT -> Hex.decode(hex).takeIf { b -> b.all { (it.toInt() and 0xFF) in 0x20..0x7E } }?.toString(Charsets.US_ASCII)
                ?.takeIf { it.trim() == it }
            else -> hex
        }
        // Only offer the friendly form when it encodes back to exactly the same bytes.
        return friendly?.takeIf { (encode(tag, it, currency, exponent) as? Result.Ok)?.hex == hex } ?: raw
    }

    private fun hex(text: String): Result = try {
        Result.Ok(Hex.encode(Hex.decode(text)))
    } catch (e: IllegalArgumentException) {
        Result.Error("${e.message}.")
    }

    /** Fixed digit count from a format like "n 12" or "n 6 YYMMDD"; null for ranges or none. */
    private fun fixedDigits(info: TagInfo): Int? =
        Regex("""^n (\d+)(?:\s|$)""").find(info.format)?.groupValues?.get(1)?.toInt()

    private fun amount(info: TagInfo, text: String, exponent: Int): Result {
        val m = Regex("""^(\d+)(?:\.(\d+))?$""").matchEntire(text.replace(",", ""))
            ?: return Result.Error("Amount must look like 10.00.")
        val (major, minorRaw) = m.destructured
        if (minorRaw.length > exponent) return Result.Error("Amount has more than $exponent decimal places.")
        val digits = (major + minorRaw.padEnd(exponent, '0')).trimStart('0').ifEmpty { "0" }
        val size = fixedDigits(info) ?: 12
        if (digits.length > size) return Result.Error("Amount does not fit in $size digits.")
        return Result.Ok(digits.padStart(size, '0'))
    }

    private fun date(text: String): Result {
        val digits = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(text)?.let { m ->
            val (y, mo, d) = m.destructured
            if (y.toInt() !in 1950..2049) return Result.Error("EMV dates cover years 1950 to 2049.")
            y.substring(2) + mo + d
        } ?: text.takeIf { it.length == 6 && it.all(Char::isDigit) }
            ?: return Result.Error("Date must be YYYY-MM-DD or YYMMDD.")
        val yy = digits.substring(0, 2).toInt()
        val year = if (yy < 50) 2000 + yy else 1900 + yy
        val valid = runCatching { java.time.LocalDate.of(year, digits.substring(2, 4).toInt(), digits.substring(4).toInt()) }.isSuccess
        return if (valid) Result.Ok(digits) else Result.Error("'$text' is not a valid date.")
    }

    private fun time(text: String): Result {
        val digits = text.replace(":", "")
        if (digits.length != 6 || !digits.all(Char::isDigit)) return Result.Error("Time must be HH:MM:SS.")
        val (h, m, s) = listOf(0, 2, 4).map { digits.substring(it, it + 2).toInt() }
        return if (h < 24 && m < 60 && s < 60) Result.Ok(digits) else Result.Error("'$text' is not a valid time.")
    }

    private fun currencyOf(text: String) =
        if (text.all(Char::isDigit)) tables.currency(text) else tables.currencyByAlpha(text)

    private fun country(text: String): Result {
        val c = if (text.all(Char::isDigit)) tables.country(text) else tables.countryByAlpha(text)
        return c?.let { Result.Ok("0" + it.numeric) } ?: Result.Error("Unknown country '$text'.")
    }

    companion object {
        /** Prefix that makes any tag's input raw hex. */
        const val RAW_PREFIX = "hex:"
    }

    private fun numeric(info: TagInfo, text: String, leftPad: Boolean): Result {
        if (text.isEmpty() || !text.all(Char::isDigit)) return Result.Error("Digits only.")
        val size = fixedDigits(info)
        if (size != null && text.length > size) return Result.Error("At most $size digits.")
        val digits = when {
            size != null && leftPad -> text.padStart(size + size % 2, '0')
            text.length % 2 == 0 -> text
            leftPad -> "0$text"
            else -> text + "F"
        }
        return Result.Ok(digits)
    }
}
