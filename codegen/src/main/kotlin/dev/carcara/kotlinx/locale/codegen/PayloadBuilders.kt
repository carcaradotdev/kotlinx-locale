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
    select: (PartialLocaleExtras) -> Map<String, MutableMap<String, String>>,
): Map<String, String> {
    val payloads = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        payloads[canonicalTag(id)] = extras.resolveCompact(id, select).entries
            .sortedBy { it.key }
            .joinToString(LIST_SEPARATOR) { (key, pattern) -> "$key=$pattern" }
    }
    return payloads
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
            entries(patterns)
    }
    return payloads
}
