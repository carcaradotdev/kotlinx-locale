@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
import dev.carcara.kotlinx.locale.currency.cldr.displayName
import dev.carcara.kotlinx.locale.currency.cldr.format
import dev.carcara.kotlinx.locale.currency.cldr.parseFormatted
import dev.carcara.kotlinx.locale.currency.cldr.symbol
import dev.carcara.kotlinx.locale.currency.forCode
import dev.carcara.kotlinx.locale.number.SignDisplay

/** The full currency surface: symbols, names, formatting and parsing. */
@JsExport
public fun probe(code: String, tag: String, minorUnits: String, text: String): String {
    val locale = Locale.forLanguageTag(tag)
    val money = Currency.forCode(code)
    val amount = CurrencyAmount(money, minorUnits.toLong())
    return listOf(
        amount.format(locale),
        amount.format(locale, CurrencySymbolStyle.CODE, signDisplay = SignDisplay.ACCOUNTING, cash = true),
        money.symbol(locale),
        money.displayName(locale),
        CurrencyAmount.parseFormatted(money, text, locale).toDecimalString(),
    ).joinToString(" ")
}
