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

@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.number.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.FormattedNumber
import dev.carcara.kotlinx.locale.number.NumberFormatOptions
import dev.carcara.kotlinx.locale.number.NumberSymbols
import dev.carcara.kotlinx.locale.number.PluralCategory
import dev.carcara.kotlinx.locale.number.internal.roundToSignificantDigits

/**
 * How many significant digits compact notation keeps when nobody says otherwise.
 *
 * UTS #35 leaves this open. It says only that "the significant digits are
 * adjusted for consistency, typically to 2 or 3 digits, and the maximum
 * fractional digits are set to 0", and then that "APIs may, however, allow these
 * default behaviors to be overridden". "Typically" is not a specification, and
 * that latitude is why two implementations of the same document disagree about
 * the same number.
 *
 * So it is pinned here rather than left open: round half-even to whichever is
 * the more precise of zero fraction digits and two significant digits. That is
 * ICU's `Precision.integer().withMinDigits(2)` and `Intl.NumberFormat`'s compact
 * default, so 1200 is `1.2K`, 12345 is `12K` and 123456 is `123K` on every
 * target and in both reference implementations. The goldens are what hold it
 * there.
 */
private const val COMPACT_SIGNIFICANT_DIGITS = 2

/**
 * The smallest `minimumGroupingDigits` compact notation will use.
 *
 * Not in UTS #35. ICU's compact notation defaults to `GroupingStrategy.MIN2`
 * and `Intl.NumberFormat` does the same, so a locale whose own minimum is one
 * still writes a four-digit compact result ungrouped.
 */
private const val COMPACT_GROUPING_FLOOR = 2

/**
 * Picks the plural category of the divided value, which is step 8 of the compact
 * algorithm.
 *
 * A named interface rather than a bare lambda so the currency domain can hold
 * one without naming the plural source it came from.
 */
@InternalKotlinxLocaleApi
public fun interface FormattedNumberSelector {
    public fun categoryOf(number: FormattedNumber): PluralCategory
}

/**
 * UTS #35's ten-step compact algorithm.
 *
 * Rounding runs before the magnitude is fixed and the magnitude is re-checked
 * afterwards, so 999999 is `1M` rather than `1000K`. The spec's ten steps do not
 * describe that re-selection; ICU and `Intl` both do it, and the goldens pin it,
 * which is what makes departing from a literal reading of the document safe.
 */
@InternalKotlinxLocaleApi
public fun formatCompact(
    value: Decimal,
    table: CompactPatternTable,
    standardPattern: NumberPattern,
    symbols: NumberSymbols,
    selectCategory: FormattedNumberSelector,
    options: NumberFormatOptions = NumberFormatOptions.Default,
    fixedFractionDigits: Int? = null,
    useCurrencySeparators: Boolean = false,
    currencyText: String = "",
    affix: AffixSubstitution = AffixSubstitution.None,
    currencySpacing: Boolean = false,
): FormattedNumber {
    if (table.isEmpty) {
        return renderPlain(value, standardPattern, symbols, options, fixedFractionDigits, useCurrencySeparators, affix, currencySpacing)
    }

    var magnitude = magnitudeOf(value)
    if (magnitude < 3) {
        // Below the smallest bucket CLDR declares, compact output is the plain
        // number: the tables start at 1000 for every locale. The precision rule
        // still applies, though, which is what makes 0.125 read as 0.12 rather
        // than in full. Compact notation is a request for an approximate
        // reading, and the magnitude it lands on does not change that.
        return renderPlain(value, standardPattern, symbols, options, fixedFractionDigits, useCurrencySeparators, affix, currencySpacing)
    }
    if (magnitude > table.maximumMagnitude) magnitude = table.maximumMagnitude
    if (!table.hasCompactForm(magnitude)) {
        return renderPlain(value, standardPattern, symbols, options, fixedFractionDigits, useCurrencySeparators, affix, currencySpacing)
    }

    var divisor = table.divisorExponent(magnitude)
    var divided = divideAndRound(value, divisor, options, fixedFractionDigits)
    // Rounding can push the value into a different entry, and the entries are
    // keyed by digit count rather than by power-of-1000 bucket: CLDR declares
    // 1000, 10000, 100000 and so on separately. So 9999 rounds to ten thousand
    // and takes the 10000 entry, which in Arabic is a different word from the
    // 1000 one, and 999999 rounds to one million and takes the 1000000 entry.
    val adjusted = minOf(magnitudeOf(divided) + divisor, table.maximumMagnitude)
    if (adjusted != magnitude) {
        if (!table.hasCompactForm(adjusted)) {
            return renderPlain(value, standardPattern, symbols, options, fixedFractionDigits, useCurrencySeparators, affix, currencySpacing)
        }
        magnitude = adjusted
        divisor = table.divisorExponent(magnitude)
        divided = divideAndRound(value, divisor, options, fixedFractionDigits)
    }

    val interim = renderNumber(divided, standardPattern, symbols, options, fixedFractionDigits, useCurrencySeparators)
    val category = selectCategory.categoryOf(interim)
    val exact = if (divided.scale == 0) divided.unscaled else null
    val pattern = table.patternOrNull(magnitude, category, false, exact)
        ?: return renderPlain(value, standardPattern, symbols, options, fixedFractionDigits, useCurrencySeparators, affix, currencySpacing)

    // The "0" sentinel means this magnitude has no compact form in this locale,
    // so the whole number is written out. Ten locales use it to override a
    // parent's entry, which is why it has to be read as a value rather than
    // skipped past as an absence.
    if (pattern == "0") {
        return renderPlain(value, standardPattern, symbols, options, fixedFractionDigits, useCurrencySeparators, affix, currencySpacing)
    }

    val alpha = currencyText.isNotEmpty() &&
        isAlphaAdjacent(NumberPattern.parse(pattern), currencyText)
    val chosen = if (alpha) table.patternOrNull(magnitude, category, true, exact) ?: pattern else pattern

    // A pattern with no digit placeholder is the whole answer on its own.
    // French declares one thousand long as `mille`, which is a word rather than
    // a number and a word, so appending the digits would write `mille1`.
    if (!hasDigitPlaceholder(chosen)) {
        val literal = renderAffix(unquote(chosen), affix)
        return FormattedNumber(literal, divided.absoluteDigits(), "", magnitude)
    }

    // The pattern's own zeros say how many integer digits the divided value has,
    // not how many to pad to: "00K" means the bucket covers two digits. Only the
    // affixes and the placeholder position matter here.
    val compactPattern = NumberPattern.parse(placeholderOf(chosen))
    return renderNumber(
        value = divided,
        pattern = compactPattern,
        symbols = symbols,
        options = options,
        fixedFractionDigits = fixedFractionDigits ?: divided.scale,
        useCurrencySeparators = useCurrencySeparators,
        affix = affix,
        compactExponent = magnitude,
        groupingFloor = COMPACT_GROUPING_FLOOR,
        currencySpacing = currencySpacing,
    )
}

/**
 * A number written out in full, but still under compact's precision and
 * grouping.
 *
 * The three ways out of the compact path all land here: a locale with no table,
 * a value below the smallest bucket, and the `"0"` sentinel. None of them is a
 * reason to fall back to plain formatting, because the caller asked for compact
 * notation and the answer is still an approximate reading. German writes 1234 as
 * `1234` here rather than as `1.234`, which is grouping, and rounds it to the
 * compact precision rather than the pattern's, which is the other half.
 */
private fun renderPlain(
    value: Decimal,
    standardPattern: NumberPattern,
    symbols: NumberSymbols,
    options: NumberFormatOptions,
    fixedFractionDigits: Int?,
    useCurrencySeparators: Boolean,
    affix: AffixSubstitution,
    currencySpacing: Boolean,
): FormattedNumber {
    val rounded = divideAndRound(value, 0, options, fixedFractionDigits)
    return renderNumber(
        value = rounded,
        pattern = standardPattern,
        symbols = symbols,
        options = options,
        fixedFractionDigits = fixedFractionDigits ?: rounded.scale,
        useCurrencySeparators = useCurrencySeparators,
        affix = affix,
        groupingFloor = COMPACT_GROUPING_FLOOR,
        currencySpacing = currencySpacing,
    )
}

/** The power of ten of [value]'s integer part: 0 for anything under ten. */
private fun magnitudeOf(value: Decimal): Int {
    var remaining = value.integerPart
    if (remaining < 0) remaining = -remaining
    var magnitude = 0
    while (remaining >= 10) {
        remaining /= 10
        magnitude++
    }
    return magnitude
}

private fun divideAndRound(value: Decimal, divisor: Int, options: NumberFormatOptions, fixedFractionDigits: Int?): Decimal {
    // Dividing by lowering the scale keeps the value exact: 123456 at scale 0
    // divided by 1000 is 123456 at scale 3, which is 123.456.
    val divided = Decimal.ofUnscaled(value.unscaled, value.scale + divisor)

    val explicit = fixedFractionDigits ?: options.maximumFractionDigits
    if (explicit != null) return divided.rescaled(explicit)

    // The pinned default: whichever of zero fraction digits and two significant
    // digits keeps more information.
    val atInteger = divided.rescaled(0)
    if (significantDigitsOf(atInteger) >= COMPACT_SIGNIFICANT_DIGITS) return atInteger
    val rounded = Decimal.ofUnscaled(
        roundToSignificantDigits(divided.unscaled, divided.scale, COMPACT_SIGNIFICANT_DIGITS),
        divided.scale,
    )
    // Trailing zeros beyond the significant digits come off, so 1.20 prints 1.2.
    var trimmed = rounded
    while (trimmed.scale > 0 && trimmed.unscaled % 10L == 0L) {
        trimmed = Decimal.ofUnscaled(trimmed.unscaled / 10, trimmed.scale - 1)
    }
    return trimmed
}

private fun significantDigitsOf(value: Decimal): Int {
    if (value.isZero) return 1
    var remaining = value.unscaled
    if (remaining < 0) remaining = -remaining
    var digits = 0
    while (remaining > 0) {
        remaining /= 10
        digits++
    }
    return digits
}

/**
 * The pattern with its run of zeros replaced by a single `0`, so the renderer
 * writes the divided value rather than padding to the bucket's width.
 */
private fun placeholderOf(pattern: String): String = buildString(pattern.length) {
    var index = 0
    var written = false
    var inQuote = false
    while (index < pattern.length) {
        val ch = pattern[index]
        when {
            ch == '\'' -> {
                inQuote = !inQuote
                append(ch)
                index++
            }
            // The negative subpattern is a pattern of its own and needs its own
            // placeholder. Swahili writes its millions as `0M;-0M`, and a flag
            // carried across the separator would leave the negative half with
            // no digits at all.
            !inQuote && ch == ';' -> {
                written = false
                append(ch)
                index++
            }
            !inQuote && ch == '0' -> {
                while (index < pattern.length && pattern[index] == '0') index++
                if (!written) {
                    append('0')
                    written = true
                }
            }
            else -> {
                append(ch)
                index++
            }
        }
    }
}

/** Whether [pattern] has a `0` outside quotes, which is where the number goes. */
private fun hasDigitPlaceholder(pattern: String): Boolean {
    var inQuote = false
    for (ch in pattern) {
        when {
            ch == '\'' -> inQuote = !inQuote
            !inQuote && ch == '0' -> return true
        }
    }
    return false
}

/** [pattern] with its LDML quoting removed, so `Mio'.'` reads `Mio.`. */
private fun unquote(pattern: String): String = buildString(pattern.length) {
    var inQuote = false
    var index = 0
    while (index < pattern.length) {
        val ch = pattern[index]
        if (ch == '\'') {
            // A doubled quote is a literal one.
            if (inQuote && index + 1 < pattern.length && pattern[index + 1] == '\'') {
                append('\'')
                index += 2
                continue
            }
            inQuote = !inQuote
            index++
            continue
        }
        append(ch)
        index++
    }
}
