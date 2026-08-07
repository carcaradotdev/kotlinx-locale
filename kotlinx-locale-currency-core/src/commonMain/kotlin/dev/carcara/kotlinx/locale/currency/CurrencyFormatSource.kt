/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource
import dev.carcara.kotlinx.locale.number.NumberGrouping
import dev.carcara.kotlinx.locale.number.NumberNotation
import dev.carcara.kotlinx.locale.number.SignDisplay

/**
 * Everything a currency format can be asked for beyond the amount and the
 * locale.
 *
 * A class rather than parameters on [CurrencyFormatSource.formatOrNull] because
 * the interface is implemented outside this build: an option added here is a
 * field, an option added to the method is a breaking change for every
 * implementor and every fallback composer.
 */
public class CurrencyFormatOptions(
    public val style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL,
    /**
     * When and how the sign is written.
     *
     * The four accounting variants select CLDR's accounting pattern, which is
     * what a separate accounting flag used to mean. They are one enum because
     * CLDR ties them: what makes `($1,234.56)` accounting is the negative
     * subpattern of `currencyFormat type="accounting"`.
     */
    public val signDisplay: SignDisplay = SignDisplay.AUTO,
    /** CLDR's cash fraction digits and cash rounding: Swiss francs round to 0.05 in cash. */
    public val cash: Boolean = false,
    /**
     * How many fraction digits to print, overriding CLDR's.
     *
     * Applied after the currency's rounding increment rather than instead of it.
     * The increment is expressed in units of CLDR's last fraction digit, so a
     * Swiss franc cash amount rounds to 0.05 on CLDR's scale and only then
     * rescales to this many digits. Asking for more digits than the amount
     * carries pads zeros, since a [CurrencyAmount] is a count of minor units and
     * there is no further information to print.
     */
    public val fractionDigits: Int? = null,
    /** Standard, or one of CLDR's compact forms: `$1.2M`. */
    public val notation: NumberNotation = NumberNotation.STANDARD,
    public val grouping: NumberGrouping = NumberGrouping.AUTO,
) {

    public companion object {
        public val Default: CurrencyFormatOptions = CurrencyFormatOptions()
    }
}

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
    public fun formatOrNull(minorUnits: Long, currencyCode: String, locale: Locale, options: CurrencyFormatOptions): String?

    /** [text] read back as ISO minor units, or `null` when it does not parse. */
    public fun parseToMinorUnitsOrNull(text: String, currencyCode: String, locale: Locale): Long?

    /**
     * The ISO code of the currency [text] is written in, or `null` when it names
     * none, names more than one, or this source cannot tell.
     *
     * Identification only: the amount is still read by
     * [parseToMinorUnitsOrNull], which needs a code to fix the fraction scale.
     *
     * Answering `null` by default, because no platform formatter exposes this
     * and a new abstract method would break every implementor. Sources that can
     * answer are the ones over CLDR's own tables.
     */
    public fun currencyCodeOrNull(text: String, locale: Locale): String? = null

    public companion object
}

/**
 * [amount] written for [locale].
 *
 * The currency is written per [style]; [signDisplay] decides whether a sign
 * appears and whether the accounting pattern is used; [cash] applies CLDR's cash
 * fraction digits and cash rounding; [fractionDigits] overrides CLDR's digit
 * count, which is what a headline figure wants when it should read `£18,500`
 * rather than `£18,500.00`.
 *
 * Falls back to `USD 12.50`, the ISO code and the plain ISO decimal, when the
 * source cannot render the amount at all.
 */
public fun CurrencyFormatSource.format(
    amount: CurrencyAmount,
    locale: Locale,
    style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL,
    signDisplay: SignDisplay = SignDisplay.AUTO,
    cash: Boolean = false,
    fractionDigits: Int? = null,
    notation: NumberNotation = NumberNotation.STANDARD,
    grouping: NumberGrouping = NumberGrouping.AUTO,
): String = formatOrNull(
    amount.minorUnits,
    amount.currency.code,
    locale,
    CurrencyFormatOptions(style, signDisplay, cash, fractionDigits, notation, grouping),
) ?: amount.toString()

/** [text] read back as an amount of [currency], or `null` when it does not parse. */
public fun CurrencyFormatSource.parseFormattedOrNull(currency: Currency, text: String, locale: Locale): CurrencyAmount? =
    parseToMinorUnitsOrNull(text, currency.code, locale)?.let { CurrencyAmount(currency, it) }

/**
 * [text] read back as an amount, taking the currency from the text itself, or
 * `null` when the currency cannot be identified or the amount does not parse.
 *
 * Identification is a lookup rather than a guess: it reads the currency written
 * in [locale]'s own spellings, and answers nothing where the text would fit more
 * than one currency. Narrow symbols are not among the spellings it reads, since
 * `$` alone names over twenty currencies in some locales, so an amount printed
 * with [CurrencySymbolStyle.NARROW_SYMBOL] needs the overload that is told which
 * currency it is.
 */
public fun CurrencyFormatSource.parseFormattedOrNull(text: String, locale: Locale): CurrencyAmount? {
    val code = currencyCodeOrNull(text, locale) ?: return null
    val currency = Currency.forCodeOrNull(code) ?: return null
    return parseToMinorUnitsOrNull(text, code, locale)?.let { CurrencyAmount(currency, it) }
}

/** Answers from [primary], and from [fallback] wherever primary has nothing. */
public class FallbackCurrencyFormats(private val primary: CurrencyFormatSource, private val fallback: CurrencyFormatSource) :
    CurrencyFormatSource {

    override val supportedLocales: Set<Locale>
        get() = primary.supportedLocales + fallback.supportedLocales

    override fun formatOrNull(minorUnits: Long, currencyCode: String, locale: Locale, options: CurrencyFormatOptions): String? =
        primary.formatOrNull(minorUnits, currencyCode, locale, options)
            ?: fallback.formatOrNull(minorUnits, currencyCode, locale, options)

    override fun parseToMinorUnitsOrNull(text: String, currencyCode: String, locale: Locale): Long? =
        primary.parseToMinorUnitsOrNull(text, currencyCode, locale)
            ?: fallback.parseToMinorUnitsOrNull(text, currencyCode, locale)

    override fun currencyCodeOrNull(text: String, locale: Locale): String? =
        primary.currencyCodeOrNull(text, locale) ?: fallback.currencyCodeOrNull(text, locale)

    public companion object
}
