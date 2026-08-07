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

package dev.carcara.kotlinx.locale.number

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource

/**
 * A source of localized number formatting.
 *
 * The `OrNull` methods are the contract an implementation fills in; the
 * extensions below are what a consumer calls, and they carry the fallback so
 * that a formatted number is never nothing.
 */
public interface NumberFormatSource : LocaleDataSource {

    /** [locale]'s number symbols, or `null` when this source has none. */
    public fun symbolsOrNull(locale: Locale): NumberSymbols?

    /** [value] written for [locale], or `null` when this source cannot render it. */
    public fun formatOrNull(value: Decimal, locale: Locale, options: NumberFormatOptions): FormattedNumber?

    /**
     * [value] written as a percentage for [locale].
     *
     * [scale] decides whether the value is multiplied by 100 first, which is a
     * question the two reference implementations answer differently; see
     * [PercentScale].
     */
    public fun formatPercentOrNull(value: Decimal, locale: Locale, scale: PercentScale, options: NumberFormatOptions): FormattedNumber?

    /** [text] read back as a [Decimal], or `null` when it does not parse in [locale]. */
    public fun parseDecimalOrNull(text: String, locale: Locale): Decimal?

    public companion object
}

/**
 * [locale]'s number symbols; falls back to [NumberSymbols.Root].
 *
 * Root is CLDR's own answer for a locale it has nothing for, not a guess, so
 * this is total.
 */
public fun NumberFormatSource.symbols(locale: Locale = Locale.current): NumberSymbols = symbolsOrNull(locale) ?: NumberSymbols.Root

/**
 * [value] written for [locale].
 *
 * Falls back to [Decimal.toPlainString], the ASCII form, when the source cannot
 * render the value at all.
 */
public fun NumberFormatSource.format(
    value: Decimal,
    locale: Locale = Locale.current,
    notation: NumberNotation = NumberNotation.STANDARD,
    signDisplay: SignDisplay = SignDisplay.AUTO,
    grouping: NumberGrouping = NumberGrouping.AUTO,
    minimumFractionDigits: Int? = null,
    maximumFractionDigits: Int? = null,
): String = formatOrNull(
    value,
    locale,
    NumberFormatOptions(notation, signDisplay, grouping, minimumFractionDigits, maximumFractionDigits),
)?.text ?: value.toPlainString()

/** [value] written for [locale], with no fraction digits. */
public fun NumberFormatSource.format(
    value: Long,
    locale: Locale = Locale.current,
    notation: NumberNotation = NumberNotation.STANDARD,
    signDisplay: SignDisplay = SignDisplay.AUTO,
    grouping: NumberGrouping = NumberGrouping.AUTO,
): String = format(Decimal.of(value), locale, notation, signDisplay, grouping)

/**
 * [value] written for [locale] at exactly [fractionDigits] digits.
 *
 * The digit count is required rather than inferred; see [Decimal.ofOrNull] for
 * why reading it off the float would make the output depend on the target.
 */
public fun NumberFormatSource.format(
    value: Double,
    fractionDigits: Int,
    locale: Locale = Locale.current,
    notation: NumberNotation = NumberNotation.STANDARD,
    signDisplay: SignDisplay = SignDisplay.AUTO,
    grouping: NumberGrouping = NumberGrouping.AUTO,
): String {
    val decimal = Decimal.ofOrNull(value, fractionDigits) ?: return value.toString()
    return format(decimal, locale, notation, signDisplay, grouping)
}

/**
 * A fraction written as a percentage: `0.075` in `en` is `7.5%`.
 *
 * Multiplies by 100, which is what a `%` in a CLDR pattern means.
 */
public fun NumberFormatSource.formatPercent(
    fraction: Decimal,
    locale: Locale = Locale.current,
    fractionDigits: Int? = null,
    signDisplay: SignDisplay = SignDisplay.AUTO,
    grouping: NumberGrouping = NumberGrouping.AUTO,
): String = formatPercentOrNull(
    fraction,
    locale,
    PercentScale.FRACTION,
    NumberFormatOptions(
        signDisplay = signDisplay,
        grouping = grouping,
        minimumFractionDigits = fractionDigits,
        maximumFractionDigits = fractionDigits,
    ),
)?.text ?: "${fraction.toPlainString()}%"

/**
 * An already-scaled percentage: `7.5` in `en` is `7.5%`.
 *
 * Does not multiply. The other name for this reading is
 * `NumberFormatter.unit(NoUnit.PERCENT)`.
 */
public fun NumberFormatSource.formatPercentValue(
    percent: Decimal,
    locale: Locale = Locale.current,
    fractionDigits: Int? = null,
    signDisplay: SignDisplay = SignDisplay.AUTO,
    grouping: NumberGrouping = NumberGrouping.AUTO,
): String = formatPercentOrNull(
    percent,
    locale,
    PercentScale.PERCENT,
    NumberFormatOptions(
        signDisplay = signDisplay,
        grouping = grouping,
        minimumFractionDigits = fractionDigits,
        maximumFractionDigits = fractionDigits,
    ),
)?.text ?: "${percent.toPlainString()}%"

/**
 * Answers from [primary], and from [fallback] wherever primary has nothing.
 *
 * The same shape the country and currency domains use, so a build can put a
 * narrowed source in front of a full one, or a platform source in front of a
 * bundled one.
 */
public class FallbackNumberFormats(private val primary: NumberFormatSource, private val fallback: NumberFormatSource) :
    NumberFormatSource {

    override val supportedLocales: Set<Locale> get() = primary.supportedLocales + fallback.supportedLocales

    override fun symbolsOrNull(locale: Locale): NumberSymbols? = primary.symbolsOrNull(locale) ?: fallback.symbolsOrNull(locale)

    override fun formatOrNull(value: Decimal, locale: Locale, options: NumberFormatOptions): FormattedNumber? =
        primary.formatOrNull(value, locale, options) ?: fallback.formatOrNull(value, locale, options)

    override fun formatPercentOrNull(value: Decimal, locale: Locale, scale: PercentScale, options: NumberFormatOptions): FormattedNumber? =
        primary.formatPercentOrNull(value, locale, scale, options)
            ?: fallback.formatPercentOrNull(value, locale, scale, options)

    override fun parseDecimalOrNull(text: String, locale: Locale): Decimal? =
        primary.parseDecimalOrNull(text, locale) ?: fallback.parseDecimalOrNull(text, locale)

    public companion object
}
