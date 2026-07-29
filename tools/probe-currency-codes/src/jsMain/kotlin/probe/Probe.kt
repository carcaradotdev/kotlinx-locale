@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.forAlpha2
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.currency.currency
import dev.carcara.kotlinx.locale.currency.forCode
import dev.carcara.kotlinx.locale.currency.isoToCldrUnits

/** Codes, unit maths and CurrencyAmount, no translated text. */
@JsExport
public fun probe(code: String, minorUnits: String, countryCode: String): String {
    val money = Currency.forCode(code)
    val amount = CurrencyAmount(money, minorUnits.toLong())
    return listOf(
        amount.toDecimalString(),
        money.code,
        money.isoToCldrUnits(amount.minorUnits).toString(),
        (amount + amount).toString(),
        Country.forAlpha2(countryCode).currency?.code,
    ).joinToString(" ")
}
