/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
 * Sparse per-locale currency plural payloads: the parent tag, the count-keyed
 * display names this locale's own file declares, the patterns that join a number
 * to one of them, and the number formatting the name form needs.
 *
 * All three data fields are keyed rather than positional, because a narrowed
 * build flattens a sparse chain by merging its keyed entries and would drop a
 * bare field past the last sparse one. The two single-entry fields hold their
 * parts under one key each, so the flattening keeps the nearest locale's whole
 * tuple rather than mixing two locales' halves.
 *
 * The number data is a copy of what `currencyFormats` already resolved rather
 * than a reference to it: those records are internal to
 * `kotlinx-locale-currency-cldr-full` and this table ships in its own artifact.
 * It is six short strings against reaching across a module boundary, and the
 * same trade `CurrencyNumberFormat` makes against the number domain.
 *
 * It is the plain decimal pattern that is carried, not the currency one. ICU
 * renders `12,34,567.89 US dollars` in Malayalam where `$1,234,567.89` groups in
 * threes, because a spelled-out name is not a `¤` in a currency pattern: the
 * number is written the way that locale writes any number, and only the
 * separators stay the currency pair.
 */
fun buildCurrencyPluralNamePayloads(flattener: Flattener, extras: ExtrasResolver): Map<String, String> {
    fun entries(map: Map<String, String>): String = map.entries
        .sortedBy { it.key }
        .joinToString(LIST_SEPARATOR) { (code, value) -> code + KEY_SEPARATOR + value }

    val unitPatterns = HashMap<String, String>()
    fun units(id: String): String = unitPatterns.getOrPut(id) {
        extras.resolveCurrencyUnitPatterns(id).joinToString(KEY_SEPARATOR)
    }

    val numberData = HashMap<String, String>()
    fun numbers(id: String): String = numberData.getOrPut(id) {
        val format = extras.resolveCurrencyFormat(id)
        listOf(
            format.digits,
            format.currencyDecimal,
            format.currencyGroup,
            format.minusSign,
            format.minimumGroupingDigits.toString(),
            extras.resolveNumberPatterns(id).decimal,
        ).joinToString(KEY_SEPARATOR)
    }

    val payloads = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        val parentId = flattener.dataChain(id).getOrNull(1)

        // Both tuples are resolved, so a locale that resolved to the same answer
        // as its parent stores nothing and the lookup walks one more hop.
        fun ownOrEmpty(key: String, resolve: (String) -> String): String {
            val own = resolve(id)
            if (parentId != null && own == resolve(parentId)) return ""
            return key + KEY_SEPARATOR + own
        }
        val partial = extras.partial(id)
        val names = LinkedHashMap<String, String>()
        for (key in partial.currencyPluralNames.keys + partial.currencyPluralNameMarkers) {
            // Deduplicated against the parent's resolved answer and nothing
            // else. Leaving out a category that matches this locale's own
            // `other` looks safe, since the runtime reads `other` when a
            // category is missing, but it is not: that read restarts at the
            // locale being asked, so a descendant that overrides `other` would
            // answer with its own plural where this locale meant the singular.
            // English drops `Papua New Guinean kina` under `one` that way and
            // en-AU then answers `Papua New Guinean kinas` for 1.
            val own = resolveCurrencyPluralName(flattener, extras, id, key) ?: continue
            if (parentId != null && own == resolveCurrencyPluralName(flattener, extras, parentId, key)) continue
            names[key] = own
        }
        payloads[canonicalTag(id)] = parentId?.let(::canonicalTag).orEmpty() + FIELD_SEPARATOR +
            entries(names) + FIELD_SEPARATOR +
            ownOrEmpty("u", ::units) + FIELD_SEPARATOR +
            ownOrEmpty("n", ::numbers)
    }
    return payloads
}

/**
 * The count-keyed currency name [id] resolves to for `"<code>#<count>"`, or null
 * when nothing in its chain declares that category at all.
 *
 * Recursive on the parent's resolved value rather than on what the parent's file
 * says, which is the whole difference between this and a plain chain walk. CLDR
 * eliminates the inheritance marker during resolution, so by the time a lookup
 * runs, a marker has already become whatever it inherited, and a child sees that
 * rather than the marker.
 *
 * Two halves, in this order. A real spelling anywhere up the chain wins, wherever
 * the markers sit relative to it. Only when the whole chain is markers does the
 * lateral half apply, and it applies at the deepest locale that both wrote a
 * marker and writes at least one real count-keyed name for the same currency,
 * reading step 4 of UTS #35's algorithm from there: `other`, then that locale's
 * own count-less display name. A locale writing only markers owns nothing and
 * contributes nothing, so its parent's table arrives whole.
 *
 * Norwegian is three of the cases in one language. `nn` writes markers under
 * both categories of the Aruban florin, owns nothing, and takes `no`'s
 * `arubiske floriner` even though its own count-less spelling is
 * `arubiske florinar`. It writes a real `one` for the Colombian peso, so there
 * it owns the currency and its `other` marker reads its own
 * `kolombianske pesos` rather than `no`'s. And `es-419` owns the tenge the same
 * way, yet its `other` marker still reads `es`'s real `tengues kazajos`,
 * because a real spelling up the chain outranks the lateral step.
 *
 * Every clause was measured rather than reasoned out, against the 966,540 names
 * ICU4J was asked to cross-check across 905 shared locales. Dropping the marker
 * outright misses 1,119. Resolving it straight to the count-less name misses
 * 9,648, across Welsh, Irish, Scottish Gaelic and a dozen more. Adding the
 * `other` step but not the ownership condition misses 2,107, across Traditional
 * Chinese, Yoruba and Dari. Running the lateral half before the vertical one
 * misses 1,215, across Latin American Spanish. What is left is 879, all of them
 * `sr-Cyrl-ME`, where ICU ships no bundle for the tag at all and answers from a
 * Latin-script one: its count-less display names differ from ours for 173 of
 * 178 currencies, so that difference is older than this table.
 */
private fun resolveCurrencyPluralName(flattener: Flattener, extras: ExtrasResolver, id: String, key: String): String? {
    val chain = flattener.dataChain(id)
    // A real spelling anywhere up the chain wins, wherever the markers sit
    // relative to it. This is the vertical half, and it is a plain walk.
    chain.firstNotNullOfOrNull { extras.partial(it).currencyPluralNames[key] }?.let { return it }

    // Nothing concrete anywhere, so the lateral half applies, at the deepest
    // locale that both wrote the marker and owns the currency's table.
    val code = key.substringBefore('#')
    val owner = chain.firstOrNull { level ->
        val partial = extras.partial(level)
        key in partial.currencyPluralNameMarkers && code in partial.currencyPluralNameCodes
    } ?: return null

    if (key != "$code#$OTHER_CATEGORY") {
        resolveCurrencyPluralName(flattener, extras, owner, "$code#$OTHER_CATEGORY")?.let { return it }
    }
    return extras.resolveValue(owner) { it.currencyNames[code] }
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
