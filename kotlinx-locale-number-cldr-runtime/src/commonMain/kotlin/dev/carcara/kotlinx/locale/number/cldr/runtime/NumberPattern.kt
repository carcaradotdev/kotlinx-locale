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

package dev.carcara.kotlinx.locale.number.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

/** The characters that make up the number core of a CLDR number pattern. */
private const val NUMBER_CHARS = "#0,."

/**
 * One parsed CLDR number pattern.
 *
 * Affixes keep `¤`, `%` and `‰` verbatim, since what they stand for is decided
 * at format time, but have quoting resolved. Only the affixes of the negative
 * subpattern are used, per UTS #35: its digits and grouping are ignored and the
 * positive subpattern's are used for both.
 *
 * This is the currency pattern parser generalised. A currency pattern never
 * needs fraction digits, because the digit count belongs to the currency, and
 * never carries `%` or a rounding increment. A decimal pattern needs all three:
 * `#,##0.###` means at least none and at most three fraction digits, and that is
 * the pattern behind every plain number this library formats.
 */
@InternalKotlinxLocaleApi
public class NumberPattern private constructor(
    public val positivePrefix: String,
    public val positiveSuffix: String,
    public val negativePrefix: String?,
    public val negativeSuffix: String?,
    public val primaryGroupSize: Int,
    public val secondaryGroupSize: Int,
    public val minimumIntegerDigits: Int,
    public val minimumFractionDigits: Int,
    public val maximumFractionDigits: Int,
    /** 100 for an unquoted `%`, 1000 for `‰`, 1 otherwise. */
    public val multiplier: Int,
    /**
     * The increment the pattern's own significant digits imply, in units of the
     * last fraction digit, or 0 for none.
     *
     * `#,##0.05` means round to five hundredths. Rare, and CLDR uses it for a
     * handful of currencies rather than for plain decimals.
     */
    public val roundingIncrement: Long,
) {

    /**
     * The same pattern with an explicit plus on positives, per the "Explicit
     * Plus Signs" rule of UTS #35.
     *
     * The rule is to take the negative subpattern and replace its minus with a
     * plus, so a locale that writes negatives as `1,0-` writes explicit
     * positives as `1,0+` rather than prefixing. Returns `this` when there is no
     * negative subpattern or no minus in it, which is what the caller checks
     * before falling back to prefixing the plus sign itself.
     */
    public fun withExplicitPlus(): NumberPattern {
        val prefix = negativePrefix ?: return this
        val suffix = negativeSuffix.orEmpty()
        if (MINUS !in prefix && MINUS !in suffix) return this
        return NumberPattern(
            positivePrefix = prefix.replace(MINUS, PLUS),
            positiveSuffix = suffix.replace(MINUS, PLUS),
            negativePrefix = negativePrefix,
            negativeSuffix = negativeSuffix,
            primaryGroupSize = primaryGroupSize,
            secondaryGroupSize = secondaryGroupSize,
            minimumIntegerDigits = minimumIntegerDigits,
            minimumFractionDigits = minimumFractionDigits,
            maximumFractionDigits = maximumFractionDigits,
            multiplier = multiplier,
            roundingIncrement = roundingIncrement,
        )
    }

    public companion object {

        private const val MINUS = '-'
        private const val PLUS = '+'

        public fun parse(pattern: String): NumberPattern {
            val subpatterns = splitUnquoted(pattern)
            val positive = parseSubpattern(subpatterns[0])
            val negative = subpatterns.getOrNull(1)?.let(::parseSubpattern)
            return NumberPattern(
                positivePrefix = positive.prefix,
                positiveSuffix = positive.suffix,
                negativePrefix = negative?.prefix,
                negativeSuffix = negative?.suffix,
                primaryGroupSize = positive.primaryGroup,
                secondaryGroupSize = positive.secondaryGroup,
                minimumIntegerDigits = positive.minimumIntegerDigits,
                minimumFractionDigits = positive.minimumFractionDigits,
                maximumFractionDigits = positive.maximumFractionDigits,
                multiplier = positive.multiplier,
                roundingIncrement = positive.roundingIncrement,
            )
        }
    }
}

private class Subpattern(
    val prefix: String,
    val suffix: String,
    val primaryGroup: Int,
    val secondaryGroup: Int,
    val minimumIntegerDigits: Int,
    val minimumFractionDigits: Int,
    val maximumFractionDigits: Int,
    val multiplier: Int,
    val roundingIncrement: Long,
)

private fun splitUnquoted(pattern: String): List<String> {
    var inQuote = false
    for (index in pattern.indices) {
        when {
            pattern[index] == '\'' -> inQuote = !inQuote
            pattern[index] == ';' && !inQuote ->
                return listOf(pattern.substring(0, index), pattern.substring(index + 1))
        }
    }
    return listOf(pattern)
}

private fun parseSubpattern(subpattern: String): Subpattern {
    var coreStart = -1
    var coreEnd = -1
    var inQuote = false
    var multiplier = 1
    for (index in subpattern.indices) {
        val ch = subpattern[index]
        when {
            ch == '\'' -> inQuote = !inQuote
            inQuote -> Unit
            ch in NUMBER_CHARS -> {
                if (coreStart < 0) coreStart = index
                coreEnd = index + 1
            }
            // Outside quotes these are not literals: UTS #35 says the value is
            // multiplied before formatting, which is what makes 1.23 print 123%.
            ch == '%' -> multiplier = 100
            ch == '‰' -> multiplier = 1000
        }
    }
    if (coreStart < 0) {
        return Subpattern(unquote(subpattern), "", 3, 3, 1, 0, 0, multiplier, 0L)
    }

    val core = subpattern.substring(coreStart, coreEnd)
    val integerPart = core.substringBefore('.')
    val fractionPart = if ('.' in core) core.substringAfter('.') else ""

    var primaryGroup = 0
    var secondaryGroup = 0
    var sinceGroup = -1
    var minimumIntegerDigits = 0
    for (ch in integerPart) {
        when (ch) {
            ',' -> {
                if (sinceGroup > 0) secondaryGroup = sinceGroup
                sinceGroup = 0
            }
            '#', '0' -> {
                if (sinceGroup >= 0) sinceGroup++
                if (ch == '0') minimumIntegerDigits++
            }
        }
    }
    if (sinceGroup > 0) primaryGroup = sinceGroup
    if (secondaryGroup == 0) secondaryGroup = primaryGroup

    // A trailing digit other than zero is a rounding increment: #,##0.05 rounds
    // to five hundredths. The zeros before it still count as minimum digits.
    var minimumFractionDigits = 0
    var maximumFractionDigits = 0
    var increment = 0L
    var incrementSeen = false
    for (ch in fractionPart) {
        when {
            ch == '0' -> {
                minimumFractionDigits++
                maximumFractionDigits++
                if (incrementSeen) increment *= 10
            }
            ch == '#' -> maximumFractionDigits++
            ch in '1'..'9' -> {
                minimumFractionDigits++
                maximumFractionDigits++
                increment = increment * 10 + (ch - '0')
                incrementSeen = true
            }
        }
    }

    return Subpattern(
        prefix = unquote(subpattern.substring(0, coreStart)),
        suffix = unquote(subpattern.substring(coreEnd)),
        primaryGroup = primaryGroup,
        secondaryGroup = secondaryGroup,
        minimumIntegerDigits = if (minimumIntegerDigits > 0) minimumIntegerDigits else 1,
        minimumFractionDigits = minimumFractionDigits,
        maximumFractionDigits = maximumFractionDigits,
        multiplier = multiplier,
        roundingIncrement = if (incrementSeen) increment else 0L,
    )
}

/** Resolves CLDR pattern quoting: `''` is a literal quote, `'x'` quotes x. */
private fun unquote(text: String): String {
    if ('\'' !in text) return text
    return buildString(text.length) {
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch == '\'') {
                if (index + 1 < text.length && text[index + 1] == '\'') {
                    append('\'')
                    index++
                }
            } else {
                append(ch)
            }
            index++
        }
    }
}
