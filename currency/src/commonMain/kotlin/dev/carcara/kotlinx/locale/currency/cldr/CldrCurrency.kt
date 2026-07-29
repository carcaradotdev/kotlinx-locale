package dev.carcara.kotlinx.locale.currency.cldr

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyFormatSource
import dev.carcara.kotlinx.locale.currency.CurrencyNameSource
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
import dev.carcara.kotlinx.locale.currency.cldr.internal.bundledCurrencyLocales
import dev.carcara.kotlinx.locale.currency.cldr.internal.bundledCurrencyName
import dev.carcara.kotlinx.locale.currency.cldr.internal.bundledCurrencySymbol
import dev.carcara.kotlinx.locale.currency.cldr.internal.formatCurrency
import dev.carcara.kotlinx.locale.currency.cldr.internal.parseFormattedCurrency

/**
 * The currency symbols, display names and number formats CLDR ships, compiled
 * into this artifact.
 *
 * Formatting and parsing need the currency's fraction behavior, which is what
 * fixes the scale of an ISO minor-unit amount, so both return `null` for a code
 * this build's [Currency] does not carry.
 */
public object CldrCurrency : CurrencyNameSource, CurrencyFormatSource {

    override val supportedLocales: Set<Locale>
        get() = bundledCurrencyLocales

    override fun currencySymbolOrNull(currencyCode: String, locale: Locale): String? = bundledCurrencySymbol(currencyCode, locale)

    override fun currencyNameOrNull(currencyCode: String, locale: Locale): String? = bundledCurrencyName(currencyCode, locale)

    override fun formatOrNull(
        minorUnits: Long,
        currencyCode: String,
        locale: Locale,
        style: CurrencySymbolStyle,
        accounting: Boolean,
        cash: Boolean,
    ): String? {
        val currency = Currency.forCodeOrNull(currencyCode) ?: return null
        return formatCurrency(minorUnits, currency, locale, style, accounting, cash)
    }

    override fun parseToMinorUnitsOrNull(text: String, currencyCode: String, locale: Locale): Long? {
        val currency = Currency.forCodeOrNull(currencyCode) ?: return null
        return parseFormattedCurrency(text, currency, locale)
    }
}
