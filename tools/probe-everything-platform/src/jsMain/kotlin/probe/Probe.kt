@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.catalog.PT
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.forAlpha2
import dev.carcara.kotlinx.locale.country.platform.displayName
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.forCode
import dev.carcara.kotlinx.locale.currency.platform.displayName
import dev.carcara.kotlinx.locale.currency.platform.format
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.platform.format
import dev.carcara.kotlinx.locale.toLocale
import kotlinx.datetime.LocalDateTime

/**
 * Every domain at once over the host, against probe-everything's ceiling.
 *
 * The locale catalog is kept, exactly as probe-everything has it, so the two
 * numbers differ only in where the names and patterns come from.
 */
@JsExport
public fun probe(code: String, currencyCode: String, iso: String, minorUnits: String): String {
    val locale = PT.BR.toLocale()
    val money = Currency.forCode(currencyCode)
    val amount = CurrencyAmount(money, minorUnits.toLong())
    return listOf(
        Country.forAlpha2(code).displayName(locale),
        amount.format(locale),
        money.displayName(locale),
        LocalDateTime.parse(iso).format(FormatStyle.FULL, locale),
    ).joinToString(" ")
}
