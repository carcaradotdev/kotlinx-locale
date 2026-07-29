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
