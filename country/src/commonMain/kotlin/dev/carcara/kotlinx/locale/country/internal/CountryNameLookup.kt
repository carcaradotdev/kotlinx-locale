package dev.carcara.kotlinx.locale.country.internal

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.internal.data.countryNamesRegistry
import dev.carcara.kotlinx.locale.dataLookupTags

private const val FIELD_SEPARATOR = '\u001F'
private const val ENTRY_SEPARATOR = '\u001E'
private const val KEY_SEPARATOR = '\u001D'

/**
 * The registry payloads are sparse: `parentTag FS entries`, where entries are
 * `alpha2 KS name` joined by ES and hold only what that locale's own CLDR file
 * declares. Lookups walk the embedded parent chain, most specific tag first.
 */
internal fun countryDisplayNameFor(country: Country, locale: Locale): String? {
    var tag = startTagFor(locale)
    var hops = 0
    while (hops++ < 16) {
        val payload = countryNamesRegistry[tag] ?: return null
        val entriesStart = payload.indexOf(FIELD_SEPARATOR) + 1
        findEntry(payload, entriesStart, country.alpha2)?.let { return it }
        val parent = payload.substring(0, entriesStart - 1)
        if (parent.isEmpty()) return null
        tag = parent
    }
    return null
}

internal fun countryForDisplayName(name: String, locale: Locale): Country? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    for (country in Country.entries) {
        if (country.displayName(locale).equals(trimmed, ignoreCase = true)) return country
    }
    return null
}

@OptIn(InternalKotlinxLocaleApi::class)
private fun startTagFor(locale: Locale): String {
    for (candidate in locale.dataLookupTags()) {
        if (candidate in countryNamesRegistry) return candidate
    }
    return "root"
}

private fun findEntry(payload: String, from: Int, key: String): String? {
    var index = from
    while (index < payload.length) {
        var end = payload.indexOf(ENTRY_SEPARATOR, index)
        if (end < 0) end = payload.length
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
