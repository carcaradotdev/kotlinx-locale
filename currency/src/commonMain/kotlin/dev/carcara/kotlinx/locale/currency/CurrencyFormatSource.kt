package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource

/**
 * A source that renders and reads monetary amounts in a locale's conventions.
 *
 * The interface sits at the level of the operation rather than of the tables
 * CLDR happens to store, because no platform hands out number patterns: `Intl`
 * and `NSNumberFormatter` format, they do not describe. Amounts cross the
 * boundary as ISO minor units and an ISO 4217 code, so the contract stays
 * independent of the entry set.
 */
public interface CurrencyFormatSource : LocaleDataSource {

    /**
     * [minorUnits] of the currency with this code, written for [locale], or
     * `null` when the source cannot render it — including when it does not
     * recognize the code, since the code is what fixes the fraction scale.
     */
    public fun formatOrNull(
        minorUnits: Long,
        currencyCode: String,
        locale: Locale,
        style: CurrencySymbolStyle,
        accounting: Boolean,
        cash: Boolean,
    ): String?

    /** [text] read back as ISO minor units, or `null` when it does not parse. */
    public fun parseToMinorUnitsOrNull(text: String, currencyCode: String, locale: Locale): Long?
}

/**
 * [amount] written for [locale].
 *
 * The currency is written per [style]; [accounting] selects the accounting
 * pattern (e.g. `($1,234.56)` for negatives in en); [cash] applies CLDR's cash
 * fraction digits and cash rounding (e.g. CHF rounds to 0.05).
 *
 * Falls back to `USD 12.50`, the ISO code and the plain ISO decimal, when the
 * source cannot render the amount at all.
 */
public fun CurrencyFormatSource.format(
    amount: CurrencyAmount,
    locale: Locale,
    style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL,
    accounting: Boolean = false,
    cash: Boolean = false,
): String = formatOrNull(amount.minorUnits, amount.currency.code, locale, style, accounting, cash)
    ?: amount.toString()

/** [text] read back as an amount of [currency], or `null` when it does not parse. */
public fun CurrencyFormatSource.parseFormattedOrNull(currency: Currency, text: String, locale: Locale): CurrencyAmount? =
    parseToMinorUnitsOrNull(text, currency.code, locale)?.let { CurrencyAmount(currency, it) }

/** Answers from [primary], and from [fallback] wherever primary has nothing. */
public class FallbackCurrencyFormats(private val primary: CurrencyFormatSource, private val fallback: CurrencyFormatSource) :
    CurrencyFormatSource {

    override val supportedLocales: Set<Locale>
        get() = primary.supportedLocales + fallback.supportedLocales

    override fun formatOrNull(
        minorUnits: Long,
        currencyCode: String,
        locale: Locale,
        style: CurrencySymbolStyle,
        accounting: Boolean,
        cash: Boolean,
    ): String? = primary.formatOrNull(minorUnits, currencyCode, locale, style, accounting, cash)
        ?: fallback.formatOrNull(minorUnits, currencyCode, locale, style, accounting, cash)

    override fun parseToMinorUnitsOrNull(text: String, currencyCode: String, locale: Locale): Long? =
        primary.parseToMinorUnitsOrNull(text, currencyCode, locale)
            ?: fallback.parseToMinorUnitsOrNull(text, currencyCode, locale)
}
