package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource

/**
 * A source of localized currency symbols and display names.
 *
 * Keyed by ISO 4217 alphabetic code rather than by [Currency] so that the
 * contract does not depend on which entry set is in play.
 */
public interface CurrencyNameSource : LocaleDataSource {

    /** The currency symbol for [locale], or `null` when this source has none. */
    public fun currencySymbolOrNull(currencyCode: String, locale: Locale): String?

    /** The currency display name for [locale], or `null` when this source has none. */
    public fun currencyNameOrNull(currencyCode: String, locale: Locale): String?
}

/**
 * The symbol for [currency] in [locale], e.g. `US$` for USD in pt-BR; falls
 * back to the ISO code.
 */
public fun CurrencyNameSource.symbol(currency: Currency, locale: Locale): String =
    currencySymbolOrNull(currency.code, locale) ?: currency.code

/** The display name for [currency] in [locale]; falls back to the ISO code. */
public fun CurrencyNameSource.displayName(currency: Currency, locale: Locale): String =
    currencyNameOrNull(currency.code, locale) ?: currency.code

/**
 * Answers from [primary], and from [fallback] wherever primary has nothing.
 * Symbols and names are dispatched separately, so a primary carrying only
 * symbols composes with a source carrying only names.
 */
public class FallbackCurrencyNames(private val primary: CurrencyNameSource, private val fallback: CurrencyNameSource) :
    CurrencyNameSource {

    override val supportedLocales: Set<Locale>
        get() = primary.supportedLocales + fallback.supportedLocales

    override fun currencySymbolOrNull(currencyCode: String, locale: Locale): String? =
        primary.currencySymbolOrNull(currencyCode, locale) ?: fallback.currencySymbolOrNull(currencyCode, locale)

    override fun currencyNameOrNull(currencyCode: String, locale: Locale): String? =
        primary.currencyNameOrNull(currencyCode, locale) ?: fallback.currencyNameOrNull(currencyCode, locale)
}
