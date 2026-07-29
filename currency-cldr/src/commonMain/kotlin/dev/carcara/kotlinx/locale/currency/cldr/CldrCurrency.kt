package dev.carcara.kotlinx.locale.currency.cldr

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.CurrencyFormatSource
import dev.carcara.kotlinx.locale.currency.CurrencyNameSource
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
import dev.carcara.kotlinx.locale.currency.cldr.format.PayloadCurrencyFormats
import dev.carcara.kotlinx.locale.currency.cldr.format.PayloadCurrencyNames
import dev.carcara.kotlinx.locale.currency.cldr.internal.data.currencyFormatsRegistry
import dev.carcara.kotlinx.locale.currency.cldr.internal.data.currencyNamesRegistry

/**
 * The currency symbols, display names and number formats CLDR ships, compiled
 * into this artifact.
 *
 * All this object contributes is the tables. The lookups, the pattern-based
 * formatter and the parser live in `kotlinx-locale-currency-cldr-format`, which
 * is also what a build that generated narrowed tables binds to.
 *
 * The delegation is written out rather than expressed with `by` because both
 * interfaces declare `supportedLocales`, and being explicit about which one
 * answers is better than resolving the clash with a one-line override.
 */
public object CldrCurrency : CurrencyNameSource, CurrencyFormatSource {

    private val names = PayloadCurrencyNames(currencyNamesRegistry)
    private val formats = PayloadCurrencyFormats(currencyFormatsRegistry, currencyNamesRegistry)

    // The two tables cover the same locale set, so either answer is the same.
    override val supportedLocales: Set<Locale>
        get() = formats.supportedLocales

    override fun currencySymbolOrNull(currencyCode: String, locale: Locale): String? = names.currencySymbolOrNull(currencyCode, locale)

    override fun currencyNameOrNull(currencyCode: String, locale: Locale): String? = names.currencyNameOrNull(currencyCode, locale)

    override fun formatOrNull(
        minorUnits: Long,
        currencyCode: String,
        locale: Locale,
        style: CurrencySymbolStyle,
        accounting: Boolean,
        cash: Boolean,
    ): String? = formats.formatOrNull(minorUnits, currencyCode, locale, style, accounting, cash)

    override fun parseToMinorUnitsOrNull(text: String, currencyCode: String, locale: Locale): Long? =
        formats.parseToMinorUnitsOrNull(text, currencyCode, locale)
}
