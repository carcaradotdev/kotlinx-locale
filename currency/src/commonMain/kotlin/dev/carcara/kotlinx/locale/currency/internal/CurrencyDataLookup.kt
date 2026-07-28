package dev.carcara.kotlinx.locale.currency.internal

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.internal.data.currencyFormatsRegistry
import dev.carcara.kotlinx.locale.currency.internal.data.currencyNamesRegistry
import dev.carcara.kotlinx.locale.dataLookupTags

internal const val FIELD_SEPARATOR = '\u001F'
internal const val ENTRY_SEPARATOR = '\u001E'
internal const val KEY_SEPARATOR = '\u001D'

/**
 * Decoded number-formatting data for one locale, fully resolved at codegen time.
 * The wire format is fields joined by U+001F.
 */
internal class CurrencyLocaleFormat(payload: String) {
    private val fields = payload.split(FIELD_SEPARATOR)

    /** The ten digits of the locale's default numbering system. */
    val digits: String = fields[0]
    val decimal: String = fields[1]
    val group: String = fields[2]

    /** Decimal/group separators used inside currency values (rarely differ). */
    val currencyDecimal: String = fields[3]
    val currencyGroup: String = fields[4]
    val minusSign: String = fields[5]
    val minimumGroupingDigits: Int = fields[6].toIntOrNull() ?: 1
    val standardPattern: String = fields[7]
    val standardAlphaPattern: String = fields[8]
    val accountingPattern: String = fields[9]
    val accountingAlphaPattern: String = fields[10]
}

/** The best bundled format data for [locale]: [dataLookupTags] order, then root. */
@OptIn(InternalKotlinxLocaleApi::class)
internal fun currencyFormatFor(locale: Locale): CurrencyLocaleFormat {
    for (candidate in locale.dataLookupTags()) {
        currencyFormatsRegistry[candidate]?.let { return CurrencyLocaleFormat(it) }
    }
    return CurrencyLocaleFormat(currencyFormatsRegistry.getValue("root"))
}

/**
 * The name payloads are sparse: `parentTag FS symbolEntries FS nameEntries`,
 * entries being `code KS value` joined by ES, holding only what that locale's
 * own CLDR file declares. Lookups walk the embedded parent chain.
 */
internal fun currencySymbolFor(currency: Currency, locale: Locale): String? = lookupCurrencyName(locale, field = 1, key = currency.code)

internal fun currencyDisplayNameFor(currency: Currency, locale: Locale): String? =
    lookupCurrencyName(locale, field = 2, key = currency.code)

private fun lookupCurrencyName(locale: Locale, field: Int, key: String): String? {
    var tag = startTagFor(locale)
    var hops = 0
    while (hops++ < 16) {
        val payload = currencyNamesRegistry[tag] ?: return null
        val firstSeparator = payload.indexOf(FIELD_SEPARATOR)
        val secondSeparator = payload.indexOf(FIELD_SEPARATOR, firstSeparator + 1)
        val from = if (field == 1) firstSeparator + 1 else secondSeparator + 1
        val to = if (field == 1) secondSeparator else payload.length
        findEntry(payload, from, to, key)?.let { return it }
        val parent = payload.substring(0, firstSeparator)
        if (parent.isEmpty()) return null
        tag = parent
    }
    return null
}

@OptIn(InternalKotlinxLocaleApi::class)
private fun startTagFor(locale: Locale): String {
    for (candidate in locale.dataLookupTags()) {
        if (candidate in currencyNamesRegistry) return candidate
    }
    return "root"
}

private fun findEntry(payload: String, from: Int, to: Int, key: String): String? {
    var index = from
    while (index < to) {
        var end = payload.indexOf(ENTRY_SEPARATOR, index)
        if (end < 0 || end > to) end = to
        if (end - index > key.length &&
            payload[index + key.length] == KEY_SEPARATOR &&
            payload.regionMatches(index, key, 0, key.length)
        ) {
            return payload.substring(index + key.length + 1, end)
        }
        index = end + 1
    }
    return null
}
