@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.number.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.NumberSymbols

/** Invisible bidi and zero-width marks that CLDR affixes and symbols carry. */
@InternalKotlinxLocaleApi
public const val INVISIBLE_MARKS: String = "‎‏؜​﻿"

/** Space variants beyond `Char.isWhitespace`, the no-break family CLDR patterns use. */
@InternalKotlinxLocaleApi
public const val NON_BREAKING_SPACES: String = "    "

/**
 * Reads a formatted number back, taking the digits and separators at face value.
 *
 * Lenient about placement and spacing, strict about content: anything left over
 * that is not a digit or a separator fails the parse rather than being ignored.
 * The number of fraction digits in the text becomes the scale, so `1.50` reads
 * back as a decimal that still knows it has two, which is what the plural rules
 * need.
 */
@InternalKotlinxLocaleApi
public fun parseDecimal(text: String, symbols: NumberSymbols): Decimal? {
    var value = text.filterNot { it in INVISIBLE_MARKS }.trim()
    if (value.isEmpty()) return null

    var negative = false
    if (value.startsWith('(') && value.endsWith(')')) {
        negative = true
        value = value.substring(1, value.length - 1)
    }

    for (sign in listOf(symbols.minusSign.filterNot { it in INVISIBLE_MARKS }, "-", "−").distinct()) {
        if (sign.isEmpty()) continue
        val index = value.indexOf(sign)
        if (index >= 0) {
            negative = true
            value = value.removeRange(index, index + sign.length)
            break
        }
    }
    for (sign in listOf(symbols.plusSign.filterNot { it in INVISIBLE_MARKS }, "+").distinct()) {
        if (sign.isEmpty()) continue
        val index = value.indexOf(sign)
        if (index >= 0) {
            value = value.removeRange(index, index + sign.length)
            break
        }
    }
    if (symbols.percentSign.isNotEmpty()) value = value.replace(symbols.percentSign, "")

    value = value.filterNot { it.isWhitespace() || it in NON_BREAKING_SPACES }
    for (group in listOf(symbols.currencyGroup, symbols.group).distinct()) {
        if (group.isNotBlank()) value = value.replace(group, "")
    }
    if (value.isEmpty()) return null

    var integerText = value
    var fractionText = ""
    for (separator in listOf(symbols.currencyDecimal, symbols.decimal).distinct()) {
        if (separator.isEmpty()) continue
        val index = value.indexOf(separator)
        if (index < 0) continue
        if (value.indexOf(separator, index + separator.length) >= 0) return null
        integerText = value.substring(0, index)
        fractionText = value.substring(index + separator.length)
        if (fractionText.isEmpty()) return null
        break
    }

    val integerDigits = toAsciiDigits(integerText, symbols.digits) ?: return null
    val fractionDigits = toAsciiDigits(fractionText, symbols.digits) ?: return null
    if (integerDigits.isEmpty() && fractionDigits.isEmpty()) return null
    if (fractionDigits.length > 18) return null

    val combined = (integerDigits.ifEmpty { "0" }) + fractionDigits
    val unscaled = ((if (negative) "-" else "") + combined).toLongOrNull() ?: return null
    return Decimal.ofUnscaled(unscaled, fractionDigits.length)
}

/** Maps locale digits, and plain ASCII digits, to ASCII; `null` on any other content. */
@InternalKotlinxLocaleApi
public fun toAsciiDigits(text: String, localeDigits: List<String>): String? {
    if (text.isEmpty()) return ""
    val digitValues = HashMap<String, Char>(20)
    for ((index, digit) in localeDigits.withIndex()) {
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
