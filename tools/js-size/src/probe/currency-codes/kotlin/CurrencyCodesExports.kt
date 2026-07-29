package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
import dev.carcara.kotlinx.locale.currency.currencies
import dev.carcara.kotlinx.locale.currency.currency

/**
 * The currency module minus its CLDR text: the enum with every ISO and CLDR
 * numeric field, the lookups, the country-to-currency mapping and all of
 * [CurrencyAmount] except the pattern formatter and the localized parser.
 *
 * `symbol`, `displayName`, `format` and `parseFormatted` are the four entry
 * points that reach CLDR text, so they are the ones left out.
 */
@JsExport
fun currencyCodesSurface(tag: String, code: String, numericCode: Int, minorUnits: Double, text: String): String {
    val locale = Locale.forLanguageTag(tag)
    val units = minorUnits.toLong()

    return buildString {
        for (currency in Currency.entries) {
            append(currency.name)
            append(currency.code)
            append(currency.numericCode)
            append(currency.defaultFractionDigits)
            append(currency.minorUnitDigits)
            append(currency.cldrFractionDigits)
            append(currency.cldrRoundingIncrement)
            append(currency.cldrCashFractionDigits)
            append(currency.cldrCashRoundingIncrement)
            append(currency.isoToCldrUnits(units))
            append(currency.cldrToIsoUnits(units))
        }
        append(Currency.valueOf(code).numericCode)
        append(Currency.forCode(code).name)
        append(Currency.forCodeOrNull(code)?.name)
        append(Currency.forNumericCode(numericCode).name)
        append(Currency.forNumericCodeOrNull(numericCode)?.name)
        append(Currency.forLocaleOrNull(locale)?.name)

        for (country in Country.entries) {
            append(country.currencies.size)
            append(country.currency?.code)
            append(Currency.forCountryOrNull(country)?.code)
        }

        val amount = CurrencyAmount(Currency.forCode(code), units)
        val other = CurrencyAmount.of(Currency.forCode(code), units)
        append(amount.currency.code)
        append(amount.minorUnits)
        append(amount.majorUnits)
        append(amount.minorPart)
        append((amount + other).minorUnits)
        append((amount - other).minorUnits)
        append((-amount).minorUnits)
        append(amount.compareTo(other))
        append(amount == other)
        append(amount.hashCode())
        append(amount.toString())
        append(amount.toDecimalString())
        append(CurrencyAmount.parseOrNull(amount.currency, text)?.minorUnits)
        append(CurrencyAmount.parse(amount.currency, text).minorUnits)

        for (style in CurrencySymbolStyle.entries) {
            append(style.name)
            append(style.ordinal)
        }
    }
}
