@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.internal

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.dataLookupTags

/**
 * The record format the generated locale tables use, and the three primitives
 * every source that reads one needs.
 *
 * The format is ours rather than CLDR's, and it is shared by the tables we ship
 * and by the narrowed tables the Gradle plugin generates. Both are read by the
 * same code, so a build that generates its own data cannot decode it differently
 * from the way we encode it.
 *
 * A record is fields joined by [FIELD_SEPARATOR]. Inside a field, entries are
 * joined by [ENTRY_SEPARATOR] and each entry is `key`, [KEY_SEPARATOR], value.
 * Records come in two shapes:
 *
 * - **resolved**, where the record holds everything the locale needs and the
 *   lookup is a single map hit ([resolvedRecord]);
 * - **sparse**, where the record holds only what that locale's own CLDR file
 *   declared and field 0 names its parent, so the lookup walks a chain
 *   ([sparseRecordValue]).
 *
 * Sparse is what makes 1121 locales fit: most of them add a handful of entries
 * over their parent.
 */
@InternalKotlinxLocaleApi
public const val FIELD_SEPARATOR: Char = '\u001F'

@InternalKotlinxLocaleApi
public const val ENTRY_SEPARATOR: Char = '\u001E'

@InternalKotlinxLocaleApi
public const val KEY_SEPARATOR: Char = '\u001D'

/** How far a sparse chain is followed before giving up on a cycle. */
private const val MAX_HOPS = 16

/**
 * Every locale the registry carries data for, excluding CLDR root.
 *
 * Root is the fallback rather than a locale anyone asked for, and a source that
 * claimed to support it would report a locale that cannot be constructed from a
 * tag.
 */
@InternalKotlinxLocaleApi
public fun supportedLocalesOf(registry: Map<String, String>): Set<Locale> = registry.keys.asSequence()
    .filter { it != "root" }
    .mapTo(LinkedHashSet(registry.size)) { Locale.forLanguageTag(it) }

/**
 * The best record for [locale]: the most specific of its lookup tags that the
 * registry carries, then root.
 *
 * Returns `null` only when the registry has no root either, which is what a
 * narrowed build looks like if it was generated without a fallback locale.
 */
@OptIn(InternalKotlinxLocaleApi::class)
@InternalKotlinxLocaleApi
public fun resolvedRecord(registry: Map<String, String>, locale: Locale): String? {
    for (candidate in locale.dataLookupTags()) {
        registry[candidate]?.let { return it }
    }
    return registry["root"]
}

/**
 * The value for [key] in field [field] of [locale]'s sparse record, following the
 * parent chain for anything the locale does not declare itself.
 *
 * [field] is 1-based over the data fields, because field 0 is the parent tag.
 * [fieldCount] counts the parent, so country names (parent plus one field) pass
 * 2 and currency names (parent, symbols, names) pass 3.
 *
 * [stopBeforeRoot] refuses to climb from a locale's own record into root, which
 * a few of UTS #35's lookups ask for by name: the currency display name
 * algorithm reads its count-keyed steps "up to, but not including root" and only
 * its last step "up to root". Against CLDR's own root the distinction is
 * invisible, since root declares none of the data those steps read. It stops
 * being invisible once a narrowed build puts the fallback locale's flattened
 * record under `root`, because a step meant to find nothing then finds the
 * fallback's answer and returns it in place of the asking locale's own.
 *
 * Refusing to climb rather than refusing to read, because those are different
 * for a locale the build never generated. Such a locale resolves to root
 * directly, and root is then the substitute it is meant to answer from, so the
 * walk starts there and reads it. A locale that does have a record answers from
 * its own chain and stops at the boundary.
 */
@OptIn(InternalKotlinxLocaleApi::class)
@InternalKotlinxLocaleApi
public fun sparseRecordValue(
    registry: Map<String, String>,
    locale: Locale,
    field: Int,
    fieldCount: Int,
    key: String,
    stopBeforeRoot: Boolean = false,
): String? {
    require(field in 1 until fieldCount) { "field $field is out of range for a $fieldCount-field record" }

    var tag = startTag(registry, locale)
    var hops = 0
    while (hops++ < MAX_HOPS) {
        val record = registry[tag] ?: return null
        val bounds = fieldBounds(record, field, fieldCount)
        if (bounds != null) {
            entryValue(record, bounds.first, bounds.second, key)?.let { return it }
        }
        val parent = record.substring(0, record.indexOf(FIELD_SEPARATOR).coerceAtLeast(0))
        if (parent.isEmpty()) return null
        if (stopBeforeRoot && parent == "root") return null
        tag = parent
    }
    return null
}

/** The most specific tag the registry carries for [locale], or root. */
@OptIn(InternalKotlinxLocaleApi::class)
private fun startTag(registry: Map<String, String>, locale: Locale): String {
    for (candidate in locale.dataLookupTags()) {
        if (candidate in registry) return candidate
    }
    return "root"
}

/** Half-open `[from, to)` of the requested field, or null when the record is short. */
private fun fieldBounds(record: String, field: Int, fieldCount: Int): Pair<Int, Int>? {
    var start = 0
    var index = 0
    while (index < field) {
        val separator = record.indexOf(FIELD_SEPARATOR, start)
        if (separator < 0) return null
        start = separator + 1
        index++
    }
    if (field == fieldCount - 1) return start to record.length
    val end = record.indexOf(FIELD_SEPARATOR, start)
    return start to (if (end < 0) record.length else end)
}

/** Scans `key KS value` entries in `[from, to)` for [key]. */
private fun entryValue(record: String, from: Int, to: Int, key: String): String? {
    var index = from
    while (index < to) {
        var end = record.indexOf(ENTRY_SEPARATOR, index)
        if (end < 0 || end > to) end = to
        if (end - index > key.length &&
            record[index + key.length] == KEY_SEPARATOR &&
            record.regionMatches(index, key, 0, key.length)
        ) {
            return record.substring(index + key.length + 1, end)
        }
        index = end + 1
    }
    return null
}
