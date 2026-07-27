package dev.srsouza.kotlinx.datetime.locale.internal

import dev.srsouza.kotlinx.datetime.locale.Locale
import dev.srsouza.kotlinx.datetime.locale.internal.data.localeDataRegistry

private const val FIELD_SEPARATOR = '\u001F'
private const val LIST_SEPARATOR = '\u001E'

/**
 * Decoded CLDR gregorian data for one locale. The wire format is produced by
 * the :codegen module: fields joined by U+001F, list items by U+001E.
 */
internal class LocaleData(payload: String) {
    private val fields = payload.split(FIELD_SEPARATOR)

    val monthsWide: List<String> = fields[0].split(LIST_SEPARATOR)
    val monthsAbbr: List<String> = fields[1].split(LIST_SEPARATOR)
    val monthsNarrow: List<String> = fields[2].split(LIST_SEPARATOR)

    /** ISO order: index 0 is Monday. */
    val daysWide: List<String> = fields[3].split(LIST_SEPARATOR)
    val daysAbbr: List<String> = fields[4].split(LIST_SEPARATOR)
    val daysNarrow: List<String> = fields[5].split(LIST_SEPARATOR)

    val am: String = fields[6]
    val pm: String = fields[7]
    val era0: String = fields[8]
    val era1: String = fields[9]

    /** Index order: FULL, LONG, MEDIUM, SHORT. */
    val dateFormats: List<String> = fields.subList(10, 14)
    val timeFormats: List<String> = fields.subList(14, 18)
    val glueFormats: List<String> = fields.subList(18, 22)

    /** The ten digits of the locale's default numbering system. */
    val digits: String = fields[22]
}

/**
 * Finds the best bundled data for [locale]: exact tag first, then progressively
 * less specific candidates, finally CLDR root.
 */
internal fun localeDataFor(locale: Locale): LocaleData {
    val language = locale.language
    val script = locale.script
    val region = locale.region
    val variant = locale.variant

    val candidates = buildList {
        fun addTag(vararg parts: String?) {
            val tag = parts.filterNotNull().joinToString("-")
            if (tag.isNotEmpty() && tag !in this) add(tag)
        }
        addTag(language, script, region, variant)
        addTag(language, script, region)
        addTag(language, region, variant)
        addTag(language, region)
        addTag(language, script)
        addTag(language, variant)
        addTag(language)
    }

    for (candidate in candidates) {
        localeDataRegistry[candidate]?.let { return LocaleData(it) }
    }
    return LocaleData(localeDataRegistry.getValue("root"))
}
