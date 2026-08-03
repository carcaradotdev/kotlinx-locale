@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.datetime.cldr.runtime

import dev.carcara.kotlinx.locale.Capitalization
import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.CalendarCapitalizationSource
import dev.carcara.kotlinx.locale.datetime.CalendarNameUsage
import dev.carcara.kotlinx.locale.datetime.DateTimeFormatSource
import dev.carcara.kotlinx.locale.datetime.DurationPatternSource
import dev.carcara.kotlinx.locale.datetime.DurationStyle
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.NameContext
import dev.carcara.kotlinx.locale.datetime.TextStyle
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.resolvedRecord
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf
import dev.carcara.kotlinx.locale.titlecaseFirstWord
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

private const val LIST_SEPARATOR = '\u001E'

/**
 * A [DateTimeFormatSource] over a table of CLDR pattern records, together with
 * the parser and formatter that render them.
 *
 * The table is a constructor argument, so the shipped `-cldr-full` artifact
 * hands it the full 1121-locale set and a build that generated a narrowed set
 * hands it that instead. Both go through the same pattern machinery, which is
 * why a narrowed build formats identically to a full one for the locales it
 * kept.
 *
 * Records are fully resolved rather than sparse: a date pattern is not something
 * a locale inherits piecemeal, so each record carries everything its locale
 * needs and a lookup is one map hit.
 */
public class PayloadDateTimeFormats(
    private val records: Map<String, String>,
    /**
     * The stand-alone names, empty when this build did not generate them.
     *
     * Empty is not a half-working state: every lookup then answers with the
     * format names, which is what CLDR root's own alias says for the 838 locales
     * that declare no stand-alone form.
     */
    private val standaloneRecords: Map<String, String> = emptyMap(),
) : DateTimeFormatSource,
    CalendarCapitalizationSource,
    DurationPatternSource {

    override val supportedLocales: Set<Locale> by lazy {
        supportedLocalesOf(records)
    }

    override fun durationPatternOrNull(style: DurationStyle, locale: Locale): String? =
        recordFor(locale)?.durationPatterns?.getOrNull(style.ordinal)?.takeIf(String::isNotEmpty)

    override fun formatDateOrNull(date: LocalDate, style: FormatStyle, locale: Locale): String? {
        val data = recordFor(locale) ?: return null
        return formatPattern(parseDateTimePattern(data.dateFormats[style.ordinal]), data, date = date, time = null)
    }

    /**
     * Time-zone fields present in the FULL and LONG patterns are omitted, since
     * a [LocalTime] carries no zone information.
     */
    override fun formatTimeOrNull(time: LocalTime, style: FormatStyle, locale: Locale): String? {
        val data = recordFor(locale) ?: return null
        val tokens = parseDateTimePattern(data.timeFormats[style.ordinal]).withoutZoneFields()
        return formatPattern(tokens, data, date = null, time = time)
    }

    /**
     * Combines the locale's date and time patterns with its CLDR "glue" pattern,
     * e.g. `Sunday, July 27, 2026, 3:05 PM` for `en`.
     */
    override fun formatDateTimeOrNull(dateTime: LocalDateTime, dateStyle: FormatStyle, timeStyle: FormatStyle, locale: Locale): String? {
        val data = recordFor(locale) ?: return null
        val datePart = formatPattern(
            parseDateTimePattern(data.dateFormats[dateStyle.ordinal]),
            data,
            date = dateTime.date,
            time = null,
        )
        val timePart = formatPattern(
            parseDateTimePattern(data.timeFormats[timeStyle.ordinal]).withoutZoneFields(),
            data,
            date = null,
            time = dateTime.time,
        )
        // The glue pattern only contains literals (possibly quoted) and the
        // {1}/{0} placeholders for the date and time parts.
        val glue = formatPattern(
            parseDateTimePattern(data.glueFormats[dateStyle.ordinal]),
            data,
            date = null,
            time = null,
        )
        return glue.replace("{1}", datePart).replace("{0}", timePart)
    }

    /** The name in the CLDR "format" context, e.g. `julho` rather than `Julho`. */
    override fun monthNameOrNull(month: Int, style: TextStyle, locale: Locale): String? =
        monthNameOrNull(month, style, NameContext.FORMAT, locale)

    override fun monthNameOrNull(month: Int, style: TextStyle, context: NameContext, locale: Locale): String? {
        val data = recordFor(locale) ?: return null
        val index = month - 1
        if (index !in data.monthsWide.indices) return null
        return data.month(index, style, context)
    }

    /** The name in the CLDR "format" context, e.g. `segunda-feira`. */
    override fun dayOfWeekNameOrNull(isoDayNumber: Int, style: TextStyle, locale: Locale): String? =
        dayOfWeekNameOrNull(isoDayNumber, style, NameContext.FORMAT, locale)

    override fun dayOfWeekNameOrNull(isoDayNumber: Int, style: TextStyle, context: NameContext, locale: Locale): String? {
        val data = recordFor(locale) ?: return null
        val index = isoDayNumber - 1
        if (index !in data.daysWide.indices) return null
        return data.dayOfWeek(index, style, context)
    }

    override fun capitalized(name: String, usage: CalendarNameUsage, capitalization: Capitalization, locale: Locale): String {
        if (name.isEmpty() || capitalization == Capitalization.MIDDLE_OF_SENTENCE) return name
        val bit = usage.ordinal * 2 + if (capitalization == Capitalization.STANDALONE) 0 else 1
        val bits = recordFor(locale)?.capitalizationBits ?: return name
        if ((bits shr bit) and 1 == 0) return name
        return titlecaseFirstWord(name, locale.language)
    }

    private fun recordFor(locale: Locale): DateTimeRecord? = resolvedRecord(records, locale)
        ?.let { DateTimeRecord(it, resolvedRecord(standaloneRecords, locale)) }
}

/**
 * Decoded CLDR gregorian data for one locale.
 *
 * Public under the internal-API marker so the ICU cross-check can compare the
 * patterns directly. No source interface exposes them, because no platform could
 * implement one that did: `Intl.DateTimeFormat` formats, it does not hand out
 * patterns.
 */
@InternalKotlinxLocaleApi
public class DateTimeRecord(record: String, standaloneRecord: String? = null) {
    private val fields = record.split(FIELD_SEPARATOR)

    public val monthsWide: List<String> = fields[0].split(LIST_SEPARATOR)
    public val monthsAbbr: List<String> = fields[1].split(LIST_SEPARATOR)
    public val monthsNarrow: List<String> = fields[2].split(LIST_SEPARATOR)

    /** ISO order: index 0 is Monday. */
    public val daysWide: List<String> = fields[3].split(LIST_SEPARATOR)
    public val daysAbbr: List<String> = fields[4].split(LIST_SEPARATOR)
    public val daysNarrow: List<String> = fields[5].split(LIST_SEPARATOR)

    public val am: String = fields[6]
    public val pm: String = fields[7]
    public val era0: String = fields[8]
    public val era1: String = fields[9]

    /** Index order: FULL, LONG, MEDIUM, SHORT. */
    public val dateFormats: List<String> = fields.subList(10, 14)
    public val timeFormats: List<String> = fields.subList(14, 18)
    public val glueFormats: List<String> = fields.subList(18, 22)

    private val standalone: List<String> = standaloneRecord?.split(FIELD_SEPARATOR).orEmpty()

    /**
     * The stand-alone names, falling back to the format ones.
     *
     * A second record rather than more fields on this one, so the existing
     * twenty-five-field layout does not move and a build generated before the
     * table existed still decodes.
     */
    public val monthsStandaloneWide: List<String> = standaloneOr(0, monthsWide)
    public val monthsStandaloneAbbr: List<String> = standaloneOr(1, monthsAbbr)
    public val monthsStandaloneNarrow: List<String> = standaloneOr(2, monthsNarrow)
    public val daysStandaloneWide: List<String> = standaloneOr(3, daysWide)
    public val daysStandaloneAbbr: List<String> = standaloneOr(4, daysAbbr)
    public val daysStandaloneNarrow: List<String> = standaloneOr(5, daysNarrow)

    private fun standaloneOr(field: Int, format: List<String>): List<String> =
        standalone.getOrNull(field)?.takeIf(String::isNotEmpty)?.split(LIST_SEPARATOR) ?: format

    /**
     * CLDR's `contextTransforms` for this locale, two bits per usage.
     *
     * Zero where the locale declares none, which is not the same as "capitalize
     * anyway": 252 locales write lower-case month names and declare no
     * transform, and they mean it.
     */
    public val capitalizationBits: Int = standalone.getOrNull(6)?.toIntOrNull(16) ?: 0

    /** The month at [index] in [style] and [context]. */
    public fun month(index: Int, style: TextStyle, context: NameContext): String {
        val names = when (context) {
            NameContext.FORMAT -> when (style) {
                TextStyle.FULL -> monthsWide
                TextStyle.ABBREVIATED -> monthsAbbr
                TextStyle.NARROW -> monthsNarrow
            }
            NameContext.STANDALONE -> when (style) {
                TextStyle.FULL -> monthsStandaloneWide
                TextStyle.ABBREVIATED -> monthsStandaloneAbbr
                TextStyle.NARROW -> monthsStandaloneNarrow
            }
        }
        return names[index]
    }

    /** The weekday at [index], Monday first, in [style] and [context]. */
    public fun dayOfWeek(index: Int, style: TextStyle, context: NameContext): String {
        val names = when (context) {
            NameContext.FORMAT -> when (style) {
                TextStyle.FULL -> daysWide
                TextStyle.ABBREVIATED -> daysAbbr
                TextStyle.NARROW -> daysNarrow
            }
            NameContext.STANDALONE -> when (style) {
                TextStyle.FULL -> daysStandaloneWide
                TextStyle.ABBREVIATED -> daysStandaloneAbbr
                TextStyle.NARROW -> daysStandaloneNarrow
            }
        }
        return names[index]
    }

    /** The ten digits of the locale's default numbering system. */
    public val digits: String = fields[22]

    /**
     * Flexible day period names in code order (code - 2, see [DayPeriodCodes]);
     * "" when the locale has no name for that period.
     */
    public val dayPeriodNames: List<String> = fields[23].split(LIST_SEPARATOR)

    /** Day period rules from CLDR dayPeriods.xml: point rules first, then intervals. */
    public val dayPeriodRules: List<DayPeriodRule> = fields[24].split(LIST_SEPARATOR).map { item ->
        val (code, start, end) = item.split(',')
        DayPeriodRule(code.toInt(), start.toInt(), end.toInt())
    }

    /**
     * The `durationUnit` patterns, in `hm`, `hms`, `ms` order.
     *
     * Read positionally from the end of the record, and empty for a record
     * written before these existed. The caller supplies root's patterns in that
     * case, so an old record degrades to the answer almost every locale inherits
     * rather than failing.
     */
    public val durationPatterns: List<String> = fields.getOrNull(25)
        ?.takeIf(String::isNotEmpty)
        ?.split(LIST_SEPARATOR)
        .orEmpty()

    /** The name for a day period code, or null when this locale has none. */
    public fun dayPeriodName(code: Int): String? = when (code) {
        DayPeriodCodes.AM -> am
        DayPeriodCodes.PM -> pm
        else -> dayPeriodNames[code - 2].ifEmpty { null }
    }
}

/**
 * Day period type codes as encoded by the generator (the DAY_PERIOD_TYPES order):
 * am, pm, midnight, noon, morning1, morning2, afternoon1, afternoon2, evening1,
 * evening2, night1, night2.
 */
@InternalKotlinxLocaleApi
public object DayPeriodCodes {
    public const val AM: Int = 0
    public const val PM: Int = 1
    public const val MIDNIGHT: Int = 2
    public const val NOON: Int = 3
}

/**
 * One day period rule, times as minutes of the day. A point rule (midnight,
 * noon) has start == end; an interval rule covers `[start, end)` and wraps past
 * midnight when start > end.
 */
@InternalKotlinxLocaleApi
public class DayPeriodRule(public val code: Int, public val start: Int, public val end: Int) {
    public val isPoint: Boolean get() = start == end
}

/** The pattern record for [locale], for the ICU cross-check. */
@InternalKotlinxLocaleApi
public fun dateTimeRecordFor(
    records: Map<String, String>,
    locale: Locale,
    standaloneRecords: Map<String, String> = emptyMap(),
): DateTimeRecord = DateTimeRecord(
    requireNotNull(resolvedRecord(records, locale)) { "no datetime record for $locale and no root" },
    resolvedRecord(standaloneRecords, locale),
)
