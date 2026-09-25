package io.github.mouadai.octet.core.emv

data class Currency(val numeric: String, val alpha: String, val exponent: Int, val name: String)
data class Country(val numeric: String, val alpha2: String, val alpha3: String, val name: String)

/** ISO 4217 currencies and ISO 3166 countries, keyed by their 3-digit numeric code. */
class ReferenceTables(currencies: Collection<Currency>, countries: Collection<Country>) {
    private val currencyByCode = currencies.associateBy { it.numeric }
    private val countryByCode = countries.associateBy { it.numeric }

    fun currency(numeric: String): Currency? = currencyByCode[numeric.padStart(3, '0').takeLast(3)]
    fun country(numeric: String): Country? = countryByCode[numeric.padStart(3, '0').takeLast(3)]

    companion object {
        val default: ReferenceTables by lazy {
            val currencies = JsonResources.load("/octet/emv/iso4217.json").asArray("iso4217").map {
                val o = it.asObject("currency")
                Currency(o.requireString("numeric"), o.requireString("alpha"), o.requireString("exponent").toInt(), o.requireString("name"))
            }
            val countries = JsonResources.load("/octet/emv/iso3166.json").asArray("iso3166").map {
                val o = it.asObject("country")
                Country(o.requireString("numeric"), o.requireString("alpha2"), o.requireString("alpha3"), o.requireString("name"))
            }
            ReferenceTables(currencies, countries)
        }
    }
}
