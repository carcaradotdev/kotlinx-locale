@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.number.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.FormattedNumber
import dev.carcara.kotlinx.locale.number.NumberFormatOptions
import dev.carcara.kotlinx.locale.number.NumberGrouping
import dev.carcara.kotlinx.locale.number.NumberSymbols
import dev.carcara.kotlinx.locale.number.SignDisplay
import dev.carcara.kotlinx.locale.number.internal.rescaleFraction
import dev.carcara.kotlinx.locale.number.internal.roundToIncrement

/**
 * How a run of `¤` characters in an affix is filled in.
 *
 * A callback rather than a currency argument, because this module does not know
 * about `Currency` and must not: the layering rule that lets any `-cldr-full`
 * link against any `-types` is what keeps the number engine reusable by the
 * currency domain without either naming the other's entries.
 */
@InternalKotlinxLocaleApi
public fun interface AffixSubstitution {

    /** The text for a run of [runLength] `¤`s: 1 is the symbol, 2 the code, 3 or more the display name. */
    public fun currencyText(runLength: Int): String

    public companion object {

        /** Leaves `¤` runs untouched, which is what a non-currency pattern wants. */
        public val None: AffixSubstitution = AffixSubstitution { runLength -> "¤".repeat(runLength) }
    }
}

/**
 * Renders [value] through [pattern] with [symbols].
 *
 * [fixedFractionDigits], when given, overrides the pattern's own minimum and
 * maximum. Currency formatting always passes it: `¤#,##0.00` is CLDR's shape for
 * the pattern, and the digit count belongs to the currency rather than to the
 * pattern.
 *
 * [useCurrencySeparators] picks `currencyDecimal` and `currencyGroup` over the
 * plain pair. A handful of locales differ between the two and the rest do not.
 */
@InternalKotlinxLocaleApi
public fun renderNumber(
    value: Decimal,
    pattern: NumberPattern,
    symbols: NumberSymbols,
    options: NumberFormatOptions = NumberFormatOptions.Default,
    fixedFractionDigits: Int? = null,
    useCurrencySeparators: Boolean = false,
    affix: AffixSubstitution = AffixSubstitution.None,
    compactExponent: Int = 0,
    groupingFloor: Int = 1,
): FormattedNumber {
    val scaled = applyMultiplier(value, pattern.multiplier)

    val minimum = fixedFractionDigits ?: options.minimumFractionDigits ?: pattern.minimumFractionDigits
    val maximum = fixedFractionDigits ?: options.maximumFractionDigits ?: pattern.maximumFractionDigits
    var rendered = scaled.rescaled(maxOf(minimum, minOf(scaled.scale, maxOf(minimum, maximum))))
    if (pattern.roundingIncrement > 0 && fixedFractionDigits == null) {
        rendered = Decimal.ofUnscaled(roundToIncrement(rendered.unscaled, pattern.roundingIncrement), rendered.scale)
    }
    // Trailing zeros beyond the minimum come off, which is what a `#` in the
    // pattern's fraction part means.
    while (rendered.scale > minimum && rendered.unscaled % 10L == 0L) {
        rendered = Decimal.ofUnscaled(rendered.unscaled / 10, rendered.scale - 1)
    }

    // The sign is read off the value that arrived, not off the rounded result,
    // so -0.5 at no fraction digits keeps its minus. Both reference
    // implementations do this, and SignDisplay.NEGATIVE is the value that asks
    // for the other answer.
    val zero = rendered.unscaled == 0L
    val negative = scaled.unscaled < 0 && !(zero && options.signDisplay.suppressesNegativeZero)
    val digits = rendered.absoluteDigits()
    val fractionDigits = rendered.scale
    val integerLength = maxOf(digits.length - fractionDigits, 0)
    var integerPart = digits.substring(0, integerLength)
    var fractionPart = digits.substring(integerLength)
    while (fractionPart.length < fractionDigits) fractionPart = "0$fractionPart"
    val minimumIntegerDigits = maxOf(pattern.minimumIntegerDigits, options.minimumIntegerDigits)
    while (integerPart.length < minimumIntegerDigits) integerPart = "0$integerPart"

    val digitStrings = digitStringsOf(symbols.digits)
    val group = if (useCurrencySeparators) symbols.currencyGroup else symbols.group
    val decimal = if (useCurrencySeparators) symbols.currencyDecimal else symbols.decimal

    val body = buildString {
        appendGrouped(integerPart, pattern, symbols, options.grouping, group, digitStrings, groupingFloor)
        if (fractionPart.isNotEmpty()) {
            append(decimal)
            for (ch in fractionPart) append(digitStrings[ch - '0'])
        }
    }

    val effective = signPattern(pattern, symbols, options.signDisplay, negative, zero)
    val text = renderAffix(effective.prefix, affix) + body + renderAffix(effective.suffix, affix)
    return FormattedNumber(text, integerPart, fractionPart, compactExponent)
}

private fun applyMultiplier(value: Decimal, multiplier: Int): Decimal = when (multiplier) {
    // Scaling by dropping fraction digits rather than by multiplying keeps the
    // value exact and keeps the operand `v` honest: 0.075 at scale 3 becomes 7.5
    // at scale 1, which is three visible digits becoming one.
    100 -> if (value.scale >= 2) Decimal.ofUnscaled(value.unscaled, value.scale - 2) else times(value, 100)
    1000 -> if (value.scale >= 3) Decimal.ofUnscaled(value.unscaled, value.scale - 3) else times(value, 1000)
    else -> value
}

private fun times(value: Decimal, factor: Int): Decimal {
    var unscaled = value.unscaled
    var remaining = factor
    var scale = value.scale
    while (remaining > 1 && scale > 0) {
        remaining /= 10
        scale--
    }
    while (remaining > 1) {
        unscaled *= 10
        remaining /= 10
    }
    return Decimal.ofUnscaled(unscaled, scale)
}

private class SignedAffixes(val prefix: String, val suffix: String)

/**
 * The affixes for one value, with the sign placeholders resolved.
 *
 * A pattern carries `-` and `+` as placeholders rather than as text, and UTS #35
 * says they are replaced by the locale's `minusSign` and `plusSign`. That is not
 * cosmetic: Arabic's minus sign carries a leading bidi mark, and several locales
 * write theirs as U+2212 rather than as the ASCII hyphen, so a hard-coded `-`
 * would be the wrong character rather than a plainer spelling of the right one.
 * The same holds for `%` and `‰`.
 */
private fun signPattern(
    pattern: NumberPattern,
    symbols: NumberSymbols,
    signDisplay: SignDisplay,
    negative: Boolean,
    zero: Boolean,
): SignedAffixes {
    fun resolved(prefix: String, suffix: String) = SignedAffixes(prefix.withSymbols(symbols), suffix.withSymbols(symbols))

    if (signDisplay == SignDisplay.NEVER) {
        return resolved(pattern.positivePrefix, pattern.positiveSuffix)
    }
    if (negative) {
        val negativePrefix = pattern.negativePrefix
        return if (negativePrefix != null) {
            resolved(negativePrefix, pattern.negativeSuffix.orEmpty())
        } else {
            resolved("-" + pattern.positivePrefix, pattern.positiveSuffix)
        }
    }
    val wantsPlus = signDisplay.showsPlus && (!zero || signDisplay.signsZero)
    if (!wantsPlus) return resolved(pattern.positivePrefix, pattern.positiveSuffix)
    val explicit = pattern.withExplicitPlus()
    return if (explicit !== pattern) {
        resolved(explicit.positivePrefix, explicit.positiveSuffix)
    } else {
        resolved("+" + pattern.positivePrefix, pattern.positiveSuffix)
    }
}

private fun StringBuilder.appendGrouped(
    integerPart: String,
    pattern: NumberPattern,
    symbols: NumberSymbols,
    grouping: NumberGrouping,
    separator: String,
    digitStrings: List<String>,
    /**
     * A floor on the locale's `minimumGroupingDigits`, which compact notation
     * raises to two. Both reference implementations do this, and it is what
     * makes German write a sentinel-magnitude 1000 as `1000` while still
     * writing 12000 as `12.000`.
     */
    groupingFloor: Int,
) {
    val length = integerPart.length
    val primary = pattern.primaryGroupSize
    val minimum = when (grouping) {
        NumberGrouping.NEVER -> Int.MAX_VALUE
        NumberGrouping.ALWAYS -> 1
        NumberGrouping.AUTO -> maxOf(symbols.minimumGroupingDigits, groupingFloor)
    }
    val applyGrouping = primary > 0 && minimum != Int.MAX_VALUE && length >= primary + minimum
    for (index in 0 until length) {
        if (applyGrouping && index > 0) {
            val fromRight = length - index
            val afterPrimary = fromRight - primary
            if (afterPrimary == 0 || (afterPrimary > 0 && afterPrimary % pattern.secondaryGroupSize == 0)) {
                append(separator)
            }
        }
        append(digitStrings[integerPart[index] - '0'])
    }
}

internal fun renderAffix(affix: String, substitution: AffixSubstitution): String {
    if ('¤' !in affix) return affix
    return buildString(affix.length) {
        var index = 0
        while (index < affix.length) {
            if (affix[index] != '¤') {
                append(affix[index])
                index++
                continue
            }
            var run = 0
            while (index < affix.length && affix[index] == '¤') {
                run++
                index++
            }
            append(substitution.currencyText(run))
        }
    }
}

/**
 * CLDR's `alphaNextToNumber` variant applies when the character of the
 * substituted currency text that would sit against the number is a letter.
 */
@InternalKotlinxLocaleApi
public fun isAlphaAdjacent(pattern: NumberPattern, currencyText: String): Boolean {
    if (currencyText.isEmpty()) return false
    if (pattern.positivePrefix.endsWith('¤')) return currencyText.last().isLetter()
    if (pattern.positiveSuffix.startsWith('¤')) return currencyText.first().isLetter()
    return false
}

/** The ten digits as strings, supporting supplementary-plane numbering systems. */
@InternalKotlinxLocaleApi
public fun digitStringsOf(digits: String): List<String> {
    val result = ArrayList<String>(10)
    var index = 0
    while (index < digits.length) {
        val length = if (digits[index].isHighSurrogate() && index + 1 < digits.length) 2 else 1
        result.add(digits.substring(index, index + length))
        index += length
    }
    if (result.size != 10) {
        result.clear()
        for (ch in "0123456789") result.add(ch.toString())
    }
    return result
}

private fun digitStringsOf(digits: List<String>): List<String> = digits

/** Rescales [value] to [digits] fraction digits, half-even. */
@InternalKotlinxLocaleApi
public fun Decimal.atFractionDigits(digits: Int): Decimal = Decimal.ofUnscaled(rescaleFraction(unscaled, scale, digits), digits)

/**
 * The four sign and scale placeholders in an affix, replaced by [symbols].
 *
 * One pass rather than four `replace` calls, so a symbol that happens to contain
 * another placeholder cannot be rewritten a second time. `¤` is left alone: it
 * is the currency placeholder and belongs to the affix substitution.
 */
private fun String.withSymbols(symbols: NumberSymbols): String {
    if (none { it == '-' || it == '+' || it == '%' || it == '\u2030' }) return this
    return buildString(length) {
        for (ch in this@withSymbols) {
            when (ch) {
                '-' -> append(symbols.minusSign)
                '+' -> append(symbols.plusSign)
                '%' -> append(symbols.percentSign)
                '\u2030' -> append(symbols.perMille)
                else -> append(ch)
            }
        }
    }
}
