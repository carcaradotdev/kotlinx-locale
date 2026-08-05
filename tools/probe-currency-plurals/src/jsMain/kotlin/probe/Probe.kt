@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.cldr.plurals.formatPluralName
import dev.carcara.kotlinx.locale.currency.cldr.plurals.pluralName
import dev.carcara.kotlinx.locale.currency.forCode

/** Currency names that agree with a count, and the amounts written with one. */
@JsExport
public fun probe(code: String, tag: String, minorUnits: String): String {
    val locale = Locale.forLanguageTag(tag)
    val money = Currency.forCode(code)
    return listOf(
        CurrencyAmount(money, minorUnits.toLong()).formatPluralName(locale),
        CurrencyAmount(money, minorUnits.toLong()).formatPluralName(locale, fractionDigits = 0),
        money.pluralName(1, locale),
        money.pluralName(5, locale),
    ).joinToString(" ")
}
