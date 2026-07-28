package dev.carcara.kotlinx.locale.currency.internal

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle

/** The characters that make up the number core of a CLDR number pattern. */
private const val NUMBER_CHARS = "#0,."

/**
 * One parsed CLDR currency pattern. Affixes keep the currency placeholder `¤`
 * verbatim (substituted at format time) but have quoting resolved. Only the
 * affixes of the negative subpattern are used, per the CLDR spec.
 */
private class ParsedPattern(pattern: String) {
    val positivePrefix: String
    val positiveSuffix: String
    val negativePrefix: String?
    val negativeSuffix: String?
    val primaryGroupSize: Int
    val secondaryGroupSize: Int
    val minIntegerDigits: Int

    init {
        val subpatterns = splitUnquoted(pattern)
        val positive = parseSubpattern(subpatterns[0])
        positivePrefix = positive.prefix
        positiveSuffix = positive.suffix
        primaryGroupSize = positive.primaryGroup
        secondaryGroupSize = positive.secondaryGroup
        minIntegerDigits = positive.minIntegerDigits
        val negative = subpatterns.getOrNull(1)?.let(::parseSubpattern)
        negativePrefix = negative?.prefix
        negativeSuffix = negative?.suffix
    }
}

private class Subpattern(
    val prefix: String,
    val suffix: String,
    val primaryGroup: Int,
    val secondaryGroup: Int,
    val minIntegerDigits: Int,
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
    for (index in subpattern.indices) {
        val ch = subpattern[index]
        if (ch == '\'') {
            inQuote = !inQuote
        } else if (!inQuote && ch in NUMBER_CHARS) {
            if (coreStart < 0) coreStart = index
            coreEnd = index + 1
        }
    }
    if (coreStart < 0) return Subpattern(unquote(subpattern), "", 3, 3, 1)

    val core = subpattern.substring(coreStart, coreEnd)
    val integerPart = core.substringBefore('.')
    var primaryGroup = 0
    var secondaryGroup = 0
    var sinceGroup = -1
    var minIntegerDigits = 0
    for (ch in integerPart) {
        when (ch) {
            ',' -> {
                if (sinceGroup > 0) secondaryGroup = sinceGroup
                sinceGroup = 0
            }
            '#', '0' -> {
                if (sinceGroup >= 0) sinceGroup++
                if (ch == '0') minIntegerDigits++
            }
        }
    }
    if (sinceGroup > 0) primaryGroup = sinceGroup
    if (secondaryGroup == 0) secondaryGroup = primaryGroup

    return Subpattern(
        prefix = unquote(subpattern.substring(0, coreStart)),
        suffix = unquote(subpattern.substring(coreEnd)),
        primaryGroup = primaryGroup,
        secondaryGroup = secondaryGroup,
        minIntegerDigits = if (minIntegerDigits > 0) minIntegerDigits else 1,
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

internal fun formatCurrency(
    minorUnits: Long,
    currency: Currency,
    locale: Locale,
    style: CurrencySymbolStyle,
    accounting: Boolean,
    cash: Boolean,
): String {
    val data = currencyFormatFor(locale)
    val currencyText = when (style) {
        CurrencySymbolStyle.SYMBOL -> currency.symbol(locale)
        CurrencySymbolStyle.CODE -> currency.code
    }

    val targetDigits = if (cash) currency.cldrCashFractionDigits else currency.cldrFractionDigits
    val increment = if (cash) currency.cldrCashRoundingIncrement else currency.cldrRoundingIncrement
    var scaled = rescaleFraction(minorUnits, currency.minorUnitDigits, targetDigits)
    if (increment > 0) scaled = roundToIncrement(scaled, increment.toLong())

    val basePattern = if (accounting) data.accountingPattern else data.standardPattern
    val alphaPattern = if (accounting) data.accountingAlphaPattern else data.standardAlphaPattern
    var parsed = ParsedPattern(basePattern)
    if (alphaPattern != basePattern && isAlphaAdjacent(parsed, currencyText)) {
        parsed = ParsedPattern(alphaPattern)
    }

    val negative = scaled < 0
    val digitStrings = digitStringsOf(data.digits)
    val number = renderNumber(scaled, targetDigits, parsed, data, digitStrings)

    val prefix: String
    val suffix: String
    val negativePrefix = parsed.negativePrefix
    if (negative && negativePrefix != null) {
        prefix = negativePrefix
        suffix = parsed.negativeSuffix.orEmpty()
    } else {
        prefix = (if (negative) data.minusSign else "") + parsed.positivePrefix
        suffix = parsed.positiveSuffix
    }
    return renderAffix(prefix, currency, currencyText, locale) +
        number +
        renderAffix(suffix, currency, currencyText, locale)
}

/**
 * CLDR's alphaNextToNumber variant applies when the character of the substituted
 * currency symbol that would sit against the number is alphabetic.
 */
private fun isAlphaAdjacent(parsed: ParsedPattern, currencyText: String): Boolean {
    if (currencyText.isEmpty()) return false
    if (parsed.positivePrefix.endsWith('¤')) return currencyText.last().isLetter()
    if (parsed.positiveSuffix.startsWith('¤')) return currencyText.first().isLetter()
    return false
}

private fun renderNumber(
    scaled: Long,
    fractionDigits: Int,
    parsed: ParsedPattern,
    data: CurrencyLocaleFormat,
    digitStrings: List<String>,
): String {
    // ULong magnitude survives Long.MIN_VALUE.
    val magnitude = if (scaled < 0) 0uL - scaled.toULong() else scaled.toULong()
    val digits = magnitude.toString()
    val integerLength = maxOf(digits.length - fractionDigits, 0)
    var integerPart = digits.substring(0, integerLength)
    var fractionPart = digits.substring(integerLength)
    while (fractionPart.length < fractionDigits) fractionPart = "0" + fractionPart
    while (integerPart.length < parsed.minIntegerDigits) integerPart = "0" + integerPart

    val grouped = buildString {
        val length = integerPart.length
        val group = parsed.primaryGroupSize
        val applyGrouping = group > 0 &&
            length >= group + data.minimumGroupingDigits
        for (index in 0 until length) {
            if (applyGrouping && index > 0) {
                val fromRight = length - index
                val afterPrimary = fromRight - group
                if (afterPrimary == 0 ||
                    (afterPrimary > 0 && afterPrimary % parsed.secondaryGroupSize == 0)
                ) {
                    append(data.currencyGroup)
                }
            }
            append(digitStrings[integerPart[index] - '0'])
        }
    }

    if (fractionDigits == 0) return grouped
    return buildString {
        append(grouped)
        append(data.currencyDecimal)
        for (ch in fractionPart) append(digitStrings[ch - '0'])
    }
}

private fun renderAffix(
    affix: String,
    currency: Currency,
    currencyText: String,
    locale: Locale,
): String {
    if ('¤' !in affix) return affix
    return buildString(affix.length + currencyText.length) {
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
            when (run) {
                1 -> append(currencyText)
                2 -> append(currency.code)
                else -> append(currency.displayName(locale))
            }
        }
    }
}

/** The ten digits as strings, supporting supplementary-plane numbering systems. */
private fun digitStringsOf(digits: String): List<String> {
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
