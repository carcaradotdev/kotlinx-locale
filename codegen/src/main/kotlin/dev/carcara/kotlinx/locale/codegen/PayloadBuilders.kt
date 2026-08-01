package dev.carcara.kotlinx.locale.codegen

/**
 * The payload builders: the half of generation that reads CLDR. They turn the
 * flattened locale tree into the compact records the emitters and the published
 * bundle both carry.
 */

/**
 * Sparse per-locale country-name payloads: the parent tag, then only the names
 * this locale's own file declares. The runtime walks the parent chain.
 */
fun buildCountryNamePayloads(flattener: Flattener, extras: ExtrasResolver): Map<String, String> {
    val payloads = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        val partial = extras.partial(id)
        val parentTag = flattener.dataChain(id).getOrNull(1)?.let(::canonicalTag).orEmpty()
        val entries = partial.territoryNames.entries
            .sortedBy { it.key }
            .joinToString(LIST_SEPARATOR) { (code, name) -> code + KEY_SEPARATOR + name }
        payloads[canonicalTag(id)] = parentTag + FIELD_SEPARATOR + entries
    }
    return payloads
}

/**
 * Fully resolved number-formatting payloads per locale, for the currency
 * formatter: digits, separators, minus sign, and the four currency patterns.
 */
fun buildCurrencyFormatPayloads(flattener: Flattener, extras: ExtrasResolver): Map<String, String> {
    val payloads = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        val format = extras.resolveCurrencyFormat(id)
        payloads[canonicalTag(id)] = listOf(
            format.digits,
            format.decimal,
            format.group,
            format.currencyDecimal,
            format.currencyGroup,
            format.minusSign,
            format.minimumGroupingDigits.toString(),
            format.standardPattern,
            format.standardAlphaPattern,
            format.accountingPattern,
            format.accountingAlphaPattern,
        ).joinToString(FIELD_SEPARATOR)
    }
    return payloads
}

/** Fully resolved number symbols per locale: eighteen fields, all of CLDR's `<symbols>`. */
fun buildNumberSymbolPayloads(flattener: Flattener, extras: ExtrasResolver): Map<String, String> {
    val payloads = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        val symbols = extras.resolveNumberSymbols(id)
        payloads[canonicalTag(id)] = listOf(
            symbols.numberingSystem,
            symbols.digits,
            symbols.decimal,
            symbols.group,
            symbols.currencyDecimal,
            symbols.currencyGroup,
            symbols.minusSign,
            symbols.plusSign,
            symbols.percentSign,
            symbols.perMille,
            symbols.approximatelySign,
            symbols.exponential,
            symbols.superscriptingExponent,
            symbols.infinity,
            symbols.nan,
            symbols.listSeparator,
            symbols.timeSeparator,
            symbols.minimumGroupingDigits.toString(),
        ).joinToString(FIELD_SEPARATOR)
    }
    return payloads
}

/** The plain decimal and percent patterns per locale. */
fun buildNumberPatternPayloads(flattener: Flattener, extras: ExtrasResolver): Map<String, String> {
    val payloads = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        val patterns = extras.resolveNumberPatterns(id)
        payloads[canonicalTag(id)] = patterns.decimal + FIELD_SEPARATOR + patterns.percent
    }
    return payloads
}

/**
 * One compact table per locale, fully resolved.
 *
 * Resolved rather than sparse because compact is not the shape sparse pays off
 * on: of the locales that declare a short decimal table, the average declares
 * almost all twenty-four of its patterns, so a sparse record would carry the
 * whole table anyway and add a chain walk per lookup.
 */
fun buildCompactPayloads(
    flattener: Flattener,
    extras: ExtrasResolver,
    /**
     * The table an unresolved entry falls back to, or `null` for none.
     *
     * Only the long table has one. Root declares it as `<alias source="locale"
     * path="../decimalFormatLength[@type='short']"/>`, and `source="locale"`
     * means the locale being resolved rather than root, so a long entry nothing
     * overrode is that locale's own short entry. Japanese writes 12000 long as
     * `1.2万` for this reason: its long table defers entirely rather than root
     * having a word for ten thousand. Welsh is the case that shows the marker
     * has to be read rather than dropped, since its long table declares
     * `1000-count-two` as the inheritance marker while declaring
     * `1000-count-other` outright, and the two resolve to different patterns.
     */
    fallback: ((PartialLocaleExtras) -> Map<String, MutableMap<String, String>>)? = null,
    select: (PartialLocaleExtras) -> Map<String, MutableMap<String, String>>,
): Map<String, String> {
    val payloads = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        val declared = extras.resolveCompact(id, select)
        val merged = if (fallback == null) {
            // No fallback table, so a marker means the locale defers to its own
            // `other` at that magnitude, which the runtime lookup already does.
            declared.filterValues { it != COMPACT_INHERIT }
        } else {
            mergeCompactWithFallback(declared, extras.resolveCompact(id, fallback))
        }
        payloads[canonicalTag(id)] = merged.entries
            .sortedBy { it.key }
            .joinToString(LIST_SEPARATOR) { (key, pattern) -> "$key=$pattern" }
    }
    return payloads
}

/** The magnitude prefix of a compact key, so `6:other:a` gives `6:`. */
private fun magnitudePrefix(key: String): String = key.substringBefore(':') + ':'

/**
 * The long table over the short one, with every marker resolved.
 *
 * Seeded from the short table so a magnitude the long table never mentions
 * still answers, then overridden by whatever the long table states outright,
 * then markers filled from short. Entries that end up equal to their own
 * magnitude's `other` are dropped, because the runtime lookup falls back to
 * `other` anyway and carrying them would grow every locale's record for
 * nothing.
 */
private fun mergeCompactWithFallback(declared: Map<String, String>, fallback: Map<String, String>): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for ((key, value) in fallback) if (value != COMPACT_INHERIT) result[key] = value
    for ((key, value) in declared) {
        // A marker says this locale does not override the entry, so whatever
        // the fallback table already put there stands. Japanese declares its
        // whole long table this way and reads its own short patterns as a
        // result.
        if (value != COMPACT_INHERIT) result[key] = value
    }
    return result.filterKeys { key ->
        key.endsWith(":other") || result[key] != result[magnitudePrefix(key) + "other"]
    }
}

/**
 * Sparse per-locale currency-name payloads: the parent tag, the symbols this
 * locale's own file declares, and the display names it declares.
 */
fun buildCurrencyNamePayloads(flattener: Flattener, extras: ExtrasResolver): Map<String, String> {
    fun entries(map: Map<String, String>): String = map.entries
        .sortedBy { it.key }
        .joinToString(LIST_SEPARATOR) { (code, value) -> code + KEY_SEPARATOR + value }

    val payloads = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        val partial = extras.partial(id)
        val parentTag = flattener.dataChain(id).getOrNull(1)?.let(::canonicalTag).orEmpty()
        payloads[canonicalTag(id)] = parentTag + FIELD_SEPARATOR +
            entries(partial.currencySymbols) + FIELD_SEPARATOR +
            entries(partial.currencyNames)
    }
    return payloads
}

/**
 * Sparse per-locale display-name payloads: the parent tag, then the language,
 * script and territory names this locale's own file declares, then its three
 * composition patterns.
 *
 * The territory table here is the unfiltered one, macro-regions included,
 * because naming `es-419` needs `419` and the country enum does not carry it.
 * That means a build taking both this domain and the country domain holds two
 * copies of the ISO 3166-1 names. Deliberate: the alternative is a locale name
 * that cannot name its own region unless the consumer also took the country
 * artifact, which is a surprising way for the flagship call to fail.
 */
fun buildLocaleDisplayNamePayloads(flattener: Flattener, extras: ExtrasResolver): Map<String, String> {
    fun entries(map: Map<String, String>): String = map.entries
        .sortedBy { it.key }
        .joinToString(LIST_SEPARATOR) { (code, value) -> code + KEY_SEPARATOR + value }

    val payloads = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        val partial = extras.partial(id)
        val parentTag = flattener.dataChain(id).getOrNull(1)?.let(::canonicalTag).orEmpty()

        val languages = LinkedHashMap<String, String>(partial.languageNames)
        for ((code, name) in partial.languageShortNames) languages["$code#short"] = name

        // The three patterns share one key, so a locale that declares any of
        // them declares the set and a lookup walks the chain once.
        val patterns = if (
            partial.localePattern == null &&
            partial.localeSeparator == null &&
            partial.localeKeyTypePattern == null
        ) {
            emptyMap()
        } else {
            mapOf(
                "p" to listOf(
                    partial.localePattern.orEmpty(),
                    partial.localeSeparator.orEmpty(),
                    partial.localeKeyTypePattern.orEmpty(),
                ).joinToString(LIST_SEPARATOR),
            )
        }

        payloads[canonicalTag(id)] = parentTag + FIELD_SEPARATOR +
            entries(languages) + FIELD_SEPARATOR +
            entries(partial.scriptNames) + FIELD_SEPARATOR +
            entries(partial.allTerritoryNames) + FIELD_SEPARATOR +
            entries(patterns) + FIELD_SEPARATOR +
            // Appended rather than keyed, because it is one number for the whole
            // locale rather than a value per code. Resolved rather than sparse
            // for the same reason: there is nothing to walk a chain for.
            extras.resolveCapitalization(id).toString(16)
    }
    return payloads
}
