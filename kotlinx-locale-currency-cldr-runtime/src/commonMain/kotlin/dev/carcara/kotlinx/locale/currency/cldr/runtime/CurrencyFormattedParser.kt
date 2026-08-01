@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.currency.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyNameSource
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.currency.displayName
import dev.carcara.kotlinx.locale.currency.minorUnitDigits
import dev.carcara.kotlinx.locale.currency.symbol
import dev.carcara.kotlinx.locale.number.cldr.runtime.digitStringsOf

/** Invisible bidi and zero-width marks that CLDR affixes and symbols carry. */
private const val INVISIBLE_MARKS = "\u200E\u200F\u061C\u200B\uFEFF"

/** Space variants beyond Char.isWhitespace (NBSP family used by CLDR patterns). */
private const val NON_BREAKING_SPACES = "\u00A0\u202F\u2007\u2009"

/**
 * Parses a CLDR-formatted currency string back into ISO minor units.
 *
 * The number is taken at face value with the locale's separators and digits,
 * then scaled to ISO minor units. CLDR formatting digits play no role on the
 * way back: HUF prints without decimals ("200 Ft"), and parsing that returns
 * 20000, the ISO two-decimal representation. Parsing is lenient about
 * placement (any spacing, symbol/code/name position, grouping positions) and
 * strict about content: anything left over that is not a digit or separator
 * fails the parse. Negative amounts are recognized from the locale's minus
 * sign, ASCII/Unicode minus, or accounting parentheses.
 */
internal fun parseFormattedCurrency(
    data: CurrencyNumberFormat,
    names: CurrencyNameSource,
    text: String,
    currency: Currency,
    locale: Locale,
): Long? {
    var value = text.filterNot { it in INVISIBLE_MARKS }.trim()
    if (value.isEmpty()) return null

    var negative = false
    if (value.startsWith('(') && value.endsWith(')')) {
        negative = true
        value = value.substring(1, value.length - 1)
    }

    // Strip one currency representation: display name, symbol or ISO code,
    // longest first so "HUF" is not half-eaten by a shorter token.
    val tokens = listOf(
        names.displayName(currency, locale),
        names.symbol(currency, locale),
        currency.code,
    )
        .map { token -> token.filterNot { it in INVISIBLE_MARKS } }
        .filter(String::isNotEmpty)
        .sortedByDescending(String::length)
    for (token in tokens) {
        val index = value.indexOf(token, ignoreCase = true)
        if (index >= 0) {
            value = value.removeRange(index, index + token.length)
            break
        }
    }

    for (sign in listOf(data.minusSign.filterNot { it in INVISIBLE_MARKS }, "-", "−").distinct()) {
        if (sign.isEmpty()) continue
        val index = value.indexOf(sign)
        if (index >= 0) {
            negative = true
            value = value.removeRange(index, index + sign.length)
            break
        }
    }

    value = value.filterNot { it.isWhitespace() || it in NON_BREAKING_SPACES }
    for (group in listOf(data.currencyGroup, data.group).distinct()) {
        if (group.isNotBlank()) value = value.replace(group, "")
    }
    if (value.isEmpty()) return null

    var integerText = value
    var fractionText = ""
    for (separator in listOf(data.currencyDecimal, data.decimal).distinct()) {
        if (separator.isEmpty()) continue
        val index = value.indexOf(separator)
        if (index < 0) continue
        if (value.indexOf(separator, index + separator.length) >= 0) return null // two decimal points
        integerText = value.substring(0, index)
        fractionText = value.substring(index + separator.length)
        if (fractionText.isEmpty()) return null
        break
    }

    val integerDigits = toAsciiDigits(integerText, data.digits) ?: return null
    var fractionDigits = toAsciiDigits(fractionText, data.digits) ?: return null
    if (integerDigits.isEmpty() && fractionDigits.isEmpty()) return null

    // Scale to ISO minor units; surplus fraction digits are fine only when zero.
    val scaleDigits = currency.minorUnitDigits
    while (fractionDigits.length > scaleDigits && fractionDigits.endsWith('0')) {
        fractionDigits = fractionDigits.dropLast(1)
    }
    if (fractionDigits.length > scaleDigits) return null

    val combined = integerDigits + fractionDigits.padEnd(scaleDigits, '0')
    return ((if (negative) "-" else "") + combined.ifEmpty { "0" }).toLongOrNull()
}

/** Maps locale digits (and plain ASCII digits) to ASCII, or null on other content. */
private fun toAsciiDigits(text: String, localeDigits: String): String? {
    if (text.isEmpty()) return ""
    val digitValues = HashMap<String, Char>(20)
    for ((index, digit) in digitStringsOf(localeDigits).withIndex()) {
        digitValues[digit] = '0' + index
    }
    return buildString(text.length) {
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch in '0'..'9') {
                append(ch)
                index++
                continue
            }
            val length = if (ch.isHighSurrogate() && index + 1 < text.length) 2 else 1
            val mapped = digitValues[text.substring(index, index + length)] ?: return null
            append(mapped)
            index += length
        }
    }
}
