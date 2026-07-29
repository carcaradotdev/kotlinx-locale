package dev.carcara.kotlinx.locale.datetime.cldr.internal

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.dataLookupTags
import dev.carcara.kotlinx.locale.datetime.cldr.internal.data.localeDataRegistry

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

    /**
     * Flexible day period names in code order (code - 2, see [DayPeriodCodes]);
     * "" when the locale has no name for that period.
     */
    val dayPeriodNames: List<String> = fields[23].split(LIST_SEPARATOR)

    /** Day period rules from CLDR dayPeriods.xml: point rules first, then intervals. */
    val dayPeriodRules: List<DayPeriodRule> = fields[24].split(LIST_SEPARATOR).map { item ->
        val (code, start, end) = item.split(',')
        DayPeriodRule(code.toInt(), start.toInt(), end.toInt())
    }

    /** The name for a day period code, or null when this locale has none. */
    fun dayPeriodName(code: Int): String? = when (code) {
        DayPeriodCodes.AM -> am
        DayPeriodCodes.PM -> pm
        else -> dayPeriodNames[code - 2].ifEmpty { null }
    }
}

/**
 * Day period type codes as encoded by :codegen (the DAY_PERIOD_TYPES order):
 * am, pm, midnight, noon, morning1, morning2, afternoon1, afternoon2,
 * evening1, evening2, night1, night2.
 */
internal object DayPeriodCodes {
    const val AM = 0
    const val PM = 1
    const val MIDNIGHT = 2
    const val NOON = 3
}

/**
 * One day period rule, times as minutes of the day. A point rule (midnight,
 * noon) has start == end; an interval rule covers [start, end) and wraps past
 * midnight when start > end.
 */
internal class DayPeriodRule(val code: Int, val start: Int, val end: Int) {
    val isPoint: Boolean get() = start == end
}

/** Every tag the bundled tables carry, excluding CLDR root. */
internal val bundledDateTimeLocales: Set<Locale> by lazy {
    localeDataRegistry.keys.asSequence()
        .filter { it != "root" }
        .mapTo(LinkedHashSet(localeDataRegistry.size)) { Locale.forLanguageTag(it) }
}

/**
 * Finds the best bundled data for [locale]: the candidates from
 * [dataLookupTags] in order, then CLDR root.
 */
@OptIn(InternalKotlinxLocaleApi::class)
internal fun localeDataFor(locale: Locale): LocaleData {
    for (candidate in locale.dataLookupTags()) {
        localeDataRegistry[candidate]?.let { return LocaleData(it) }
    }
    return LocaleData(localeDataRegistry.getValue("root"))
}
