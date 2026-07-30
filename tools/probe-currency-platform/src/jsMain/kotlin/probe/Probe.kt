@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
import dev.carcara.kotlinx.locale.currency.forCode
import dev.carcara.kotlinx.locale.currency.platform.displayName
import dev.carcara.kotlinx.locale.currency.platform.format
import dev.carcara.kotlinx.locale.currency.platform.parseFormattedOrNull
import dev.carcara.kotlinx.locale.currency.platform.symbol

/**
 * The full currency surface over the host.
 *
 * Call for call probe-currency-full, with the one difference the API itself
 * forces: parsing is `parseFormattedOrNull` here, because the platform has no
 * parser everywhere and the return type says so.
 */
@JsExport
public fun probe(code: String, tag: String, minorUnits: String, text: String): String {
    val locale = Locale.forLanguageTag(tag)
    val money = Currency.forCode(code)
    val amount = CurrencyAmount(money, minorUnits.toLong())
    return listOf(
        amount.format(locale),
        amount.format(locale, CurrencySymbolStyle.CODE, accounting = true, cash = true),
        money.symbol(locale),
        money.displayName(locale),
        CurrencyAmount.parseFormattedOrNull(money, text, locale)?.toDecimalString(),
    ).joinToString(" ")
}
