package dev.carcara.kotlinx.locale.country.cldr.internal

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.cldr.internal.data.countryNamesRegistry
import dev.carcara.kotlinx.locale.dataLookupTags

private const val FIELD_SEPARATOR = '\u001F'
private const val ENTRY_SEPARATOR = '\u001E'
private const val KEY_SEPARATOR = '\u001D'

/** Every tag the bundled tables carry, excluding CLDR root, which names no country. */
internal val bundledCountryNameLocales: Set<Locale> by lazy {
    countryNamesRegistry.keys.asSequence()
        .filter { it != "root" }
        .mapTo(LinkedHashSet(countryNamesRegistry.size)) { Locale.forLanguageTag(it) }
}

/**
 * The registry payloads are sparse: `parentTag FS entries`, where entries are
 * `alpha2 KS name` joined by ES and hold only what that locale's own CLDR file
 * declares. Lookups walk the embedded parent chain, most specific tag first.
 */
internal fun bundledCountryName(alpha2: String, locale: Locale): String? {
    var tag = startTagFor(locale)
    var hops = 0
    while (hops++ < 16) {
        val payload = countryNamesRegistry[tag] ?: return null
        val entriesStart = payload.indexOf(FIELD_SEPARATOR) + 1
        findEntry(payload, entriesStart, alpha2)?.let { return it }
        val parent = payload.substring(0, entriesStart - 1)
        if (parent.isEmpty()) return null
        tag = parent
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
