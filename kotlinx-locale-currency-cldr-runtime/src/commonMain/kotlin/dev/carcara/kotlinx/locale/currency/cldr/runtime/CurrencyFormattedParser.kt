@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.currency.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyNameSource
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
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
    tokens: List<String>,
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

    // Strip one currency representation, longest first so "HUF" is not
    // half-eaten by a shorter token, and so "NT$" wins over the "$" inside it.
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

/**
 * The strings a currency the caller has already named is recognized by in one
 * locale, longest first.
 *
 * Built once per currency and locale rather than per parse. Each spelling is a
 * walk up the locale's parent chain, and there are six of them, so recomputing
 * them for every amount made a round trip over the bundled locales slow enough
 * to time out under Node.
 *
 * All four symbol spellings are here, narrow included, because the caller has
 * said which currency this is: nothing is being identified, so nothing can be
 * identified wrongly, and an amount written with the narrow symbol reads back.
 * Identifying a currency from the text is [CurrencyParseIndex], and that one
 * takes the narrow spellings out again.
 */
internal fun currencyParseTokens(names: CurrencyNameSource, currency: Currency, locale: Locale): List<String> = buildList {
    add(names.displayName(currency, locale))
    add(currency.code)
    for (style in SYMBOL_STYLES) add(names.symbol(currency, locale, style))
}
    .map { token -> token.filterNot { it in INVISIBLE_MARKS } }
    .filter(String::isNotEmpty)
    .distinct()
    .sortedByDescending(String::length)

/** Every spelling a currency the caller already named is recognized by. */
private val SYMBOL_STYLES = listOf(
    CurrencySymbolStyle.SYMBOL,
    CurrencySymbolStyle.NARROW_SYMBOL,
    CurrencySymbolStyle.VARIANT_SYMBOL,
    CurrencySymbolStyle.FORMAL_SYMBOL,
)

/**
 * The spellings a currency can be identified *from*, which is a smaller set.
 *
 * Narrow and formal are missing for the reason ICU leaves them out of its own
 * parse tables: they do not identify anything. CLDR disambiguates the plain
 * symbols within each locale, so en-CA writes CAD as `$` and USD as `US$` and
 * neither collides, but the narrow spellings are deliberately not disambiguated
 * and in that same locale `$` is the narrow form of more than twenty currencies.
 * Admitting them would mean picking one, and picking one is guessing.
 */
private val IDENTIFYING_SYMBOL_STYLES = listOf(CurrencySymbolStyle.SYMBOL, CurrencySymbolStyle.VARIANT_SYMBOL)

/**
 * Every string that names a currency to a reader of one locale, mapped back to
 * the ISO code, for reading an amount whose currency the caller did not say.
 *
 * Built once per locale because it walks the whole entry set: each currency
 * contributes its display name, its ISO code and the spellings in
 * [IDENTIFYING_SYMBOL_STYLES].
 *
 * A string that two currencies both claim is dropped rather than awarded to
 * either. This is stricter than ICU, which builds the same reverse map as a
 * `HashMap` and lets the last writer win, so the answer there depends on
 * resource iteration order. On the data CLDR ships the case does not arise, and
 * where it did, silently returning one of two currencies is not something money
 * should do.
 */
internal class CurrencyParseIndex(names: CurrencyNameSource, locale: Locale) {

    private val entries: List<Pair<String, String>>

    init {
        val byText = HashMap<String, String?>()
        fun offer(text: String, code: String) {
            val cleaned = text.filterNot { it in INVISIBLE_MARKS }
            if (cleaned.isEmpty()) return
            val key = cleaned.lowercase()
            // Null marks a string more than one currency answers to.
            if (key in byText && byText[key] != code) byText[key] = null else byText[key] = code
        }
        for (currency in Currency.entries) {
            offer(currency.code, currency.code)
            names.currencyNameOrNull(currency.code, locale)?.let { offer(it, currency.code) }
            for (style in IDENTIFYING_SYMBOL_STYLES) {
                names.currencySymbolOrNull(currency.code, locale, style)?.let { offer(it, currency.code) }
            }
        }
        entries = byText.entries
            .mapNotNull { (text, code) -> code?.let { text to it } }
            .sortedByDescending { it.first.length }
    }

    /**
     * The code named by the longest entry that appears in [text], or `null` when
     * none does.
     *
     * Longest first so `US$` is not read as `$`, and so a display name is not
     * half-eaten by an ISO code inside it.
     */
    fun codeIn(text: String): String? {
        val haystack = text.filterNot { it in INVISIBLE_MARKS }.lowercase()
        return entries.firstOrNull { (candidate, _) -> haystack.contains(candidate) }?.second
    }
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
