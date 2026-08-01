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
): FormattedNumber {
    if (table.isEmpty) {
        return renderNumber(value, standardPattern, symbols, options, fixedFractionDigits, useCurrencySeparators, affix)
    }

    var magnitude = magnitudeOf(value)
    if (magnitude < 3) {
        // Below the smallest bucket CLDR declares, compact output is the plain
        // number. The tables start at 1000 for every locale.
        return renderNumber(value, standardPattern, symbols, options, fixedFractionDigits, useCurrencySeparators, affix)
    }
    if (magnitude > table.maximumMagnitude) magnitude = table.maximumMagnitude

    var divided = divideAndRound(value, magnitude, options, fixedFractionDigits)
    // Rounding can push the value up a bucket: 999999 rounds to 1000 thousand,
    // which is one million.
    if (magnitude < table.maximumMagnitude && magnitudeOf(divided) >= 3) {
        magnitude += 3
        divided = divideAndRound(value, magnitude, options, fixedFractionDigits)
    }

    val interim = renderNumber(divided, standardPattern, symbols, options, fixedFractionDigits, useCurrencySeparators)
    val category = selectCategory.categoryOf(interim)
    val pattern = table.patternOrNull(magnitude, category, false)
        ?: return renderNumber(value, standardPattern, symbols, options, fixedFractionDigits, useCurrencySeparators, affix)

    // The "0" sentinel means this magnitude has no compact form in this locale.
    if (pattern == "0") {
        return renderNumber(value, standardPattern, symbols, options, fixedFractionDigits, useCurrencySeparators, affix)
    }

    val alpha = currencyText.isNotEmpty() &&
        isAlphaAdjacent(NumberPattern.parse(pattern), currencyText)
    val chosen = if (alpha) table.patternOrNull(magnitude, category, true) ?: pattern else pattern

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

private fun divideAndRound(value: Decimal, magnitude: Int, options: NumberFormatOptions, fixedFractionDigits: Int?): Decimal {
    // Dividing by lowering the scale keeps the value exact: 123456 at scale 0
    // divided by 1000 is 123456 at scale 3, which is 123.456.
    val divisor = magnitude - (magnitude % 3)
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
