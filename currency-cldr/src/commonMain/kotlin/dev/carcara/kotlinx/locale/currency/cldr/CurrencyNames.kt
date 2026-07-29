package dev.carcara.kotlinx.locale.currency.cldr

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.currency.displayName
import dev.carcara.kotlinx.locale.currency.format
import dev.carcara.kotlinx.locale.currency.parseFormattedOrNull
import dev.carcara.kotlinx.locale.currency.symbol

/**
 * The CLDR currency symbol for [locale] (e.g. `US$` for USD in pt-BR), resolved
 * through the locale's inheritance chain; falls back to the ISO code.
 */
public fun Currency.symbol(locale: Locale = Locale.current): String = CldrCurrency.symbol(this, locale)

/** The CLDR display name for [locale]; falls back to the ISO code. */
public fun Currency.displayName(locale: Locale = Locale.current): String = CldrCurrency.displayName(this, locale)

/**
 * Formats the amount with the CLDR pattern and symbols of [locale].
 *
 * The currency is written per [style]; [accounting] selects the accounting
 * pattern (e.g. `($1,234.56)` for negatives in en); [cash] applies CLDR's cash
 * fraction digits and cash rounding (e.g. CHF rounds to 0.05). The number of
 * fraction digits shown is CLDR's, which can differ from the ISO minor units —
 * the ISO to CLDR conversion rounds half-even.
 */
public fun CurrencyAmount.format(
    locale: Locale = Locale.current,
    style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL,
    accounting: Boolean = false,
    cash: Boolean = false,
): String = CldrCurrency.format(this, locale, style, accounting, cash)

/**
 * Parses a CLDR-formatted string — `R$ 1.234,56`, `($1,234.56)`, `200 Ft` —
 * back into an amount, using [locale]'s separators, digits and currency symbol.
 *
 * The printed number is taken at face value and scaled to ISO minor units, so
 * CLDR's formatting digits do not distort the result: HUF formats with no
 * decimals but has two ISO decimals, and `"200 Ft"` parses to 20000 minor
 * units. The currency may appear as its localized symbol, ISO code or display
 * name, or be absent entirely. Negatives are recognized from the locale's minus
 * sign or accounting parentheses. Returns `null` when the text has content
 * other than one number with this locale's separators, or when the fraction
 * cannot be represented in ISO minor units.
 */
public fun CurrencyAmount.Companion.parseFormattedOrNull(
    currency: Currency,
    text: String,
    locale: Locale = Locale.current,
): CurrencyAmount? = CldrCurrency.parseFormattedOrNull(currency, text, locale)

/** Like [parseFormattedOrNull] but throws on invalid input. */
public fun CurrencyAmount.Companion.parseFormatted(currency: Currency, text: String, locale: Locale = Locale.current): CurrencyAmount =
    requireNotNull(parseFormattedOrNull(currency, text, locale)) {
        "Cannot parse ${currency.code} amount: '$text'"
    }
