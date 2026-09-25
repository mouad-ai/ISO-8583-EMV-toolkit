package io.github.mouadai.octet.core.emv

/** Currency information found elsewhere in the same TLV data, used to format amounts. */
data class AmountContext(val currencyCode: String? = null, val exponent: Int? = null)

/** Turns raw tag values into human-readable text according to [DisplayKind]. */
class EmvFormatter(private val tables: ReferenceTables = ReferenceTables.default) {

    /** Result of formatting: the text plus a note when the value did not match its declared format. */
    data class Formatted(val text: String, val warning: String? = null)

    fun format(kind: DisplayKind, value: ByteArray, context: AmountContext = AmountContext()): Formatted {
        val hex = Hex.encode(value)
        return when (kind) {
            DisplayKind.HEX, DisplayKind.BITFIELD, DisplayKind.CVM, DisplayKind.CID, DisplayKind.DOL -> Formatted(hex)
            DisplayKind.NUMERIC -> digits(hex)
            DisplayKind.CN -> Formatted(hex.trimEnd('F'))
            DisplayKind.TRACK2 -> Formatted(hex.trimEnd('F'))
            DisplayKind.TEXT -> text(value)
            DisplayKind.AMOUNT -> amount(hex, context)
            DisplayKind.DATE -> date(hex)
            DisplayKind.TIME -> time(hex)
            DisplayKind.CURRENCY -> currency(hex)
            DisplayKind.COUNTRY -> country(hex)
        }
    }

    private fun isDigits(s: String) = s.isNotEmpty() && s.all { it in '0'..'9' }

    private fun digits(hex: String) =
        if (isDigits(hex)) Formatted(hex) else Formatted(hex, "Expected BCD digits")

    private fun text(value: ByteArray): Formatted {
        val printable = value.all { (it.toInt() and 0xFF) in 0x20..0x7E }
        return if (printable) Formatted(String(value, Charsets.US_ASCII))
        else Formatted(Hex.encode(value), "Not printable ASCII; shown as hex")
    }

    /** n12 amount, e.g. 000000001000 with exponent 2 = 10.00. Exponent defaults to 2 when unknown. */
    fun amount(hex: String, context: AmountContext = AmountContext()): Formatted {
        if (!isDigits(hex)) return Formatted(hex, "Expected BCD digits")
        val currency = context.currencyCode?.let { tables.currency(it) }
        val exponent = context.exponent ?: currency?.exponent ?: 2
        val minor = hex.trimStart('0').ifEmpty { "0" }.padStart(exponent + 1, '0')
        val major = minor.dropLast(exponent)
        val amount = if (exponent == 0) major else "$major.${minor.takeLast(exponent)}"
        val suffix = currency?.let { " ${it.alpha}" } ?: context.currencyCode?.let { " (currency $it)" } ?: ""
        return Formatted(amount + suffix)
    }

    /** YYMMDD; years 00-49 are 20xx and 50-99 are 19xx (EMV Book 4). */
    fun date(hex: String): Formatted {
        if (hex.length != 6 || !isDigits(hex)) return Formatted(hex, "Expected YYMMDD")
        val yy = hex.substring(0, 2).toInt()
        val mm = hex.substring(2, 4).toInt()
        val dd = hex.substring(4, 6).toInt()
        val year = if (yy < 50) 2000 + yy else 1900 + yy
        val valid = runCatching { java.time.LocalDate.of(year, mm, dd) }.isSuccess
        val text = "%04d-%02d-%02d".format(year, mm, dd)
        return if (valid) Formatted(text) else Formatted(hex, "Not a valid date")
    }

    fun time(hex: String): Formatted {
        if (hex.length != 6 || !isDigits(hex)) return Formatted(hex, "Expected HHMMSS")
        val (h, m, s) = listOf(0, 2, 4).map { hex.substring(it, it + 2).toInt() }
        return if (h < 24 && m < 60 && s < 60) Formatted("%02d:%02d:%02d".format(h, m, s))
        else Formatted(hex, "Not a valid time")
    }

    /** n3 in 2 bytes, e.g. 0504 = MAD. */
    fun currency(hex: String): Formatted {
        if (!isDigits(hex)) return Formatted(hex, "Expected BCD digits")
        val code = hex.takeLast(3)
        val c = tables.currency(code) ?: return Formatted(code, "Unknown ISO 4217 code")
        return Formatted("$code ${c.alpha} (${c.name})")
    }

    fun country(hex: String): Formatted {
        if (!isDigits(hex)) return Formatted(hex, "Expected BCD digits")
        val code = hex.takeLast(3)
        val c = tables.country(code) ?: return Formatted(code, "Unknown ISO 3166 code")
        return Formatted("$code ${c.alpha2} (${c.name})")
    }
}
