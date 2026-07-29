package dev.carcara.kotlinx.locale.datetime.cldr

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.DateTimeFormatSource
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.TextStyle
import dev.carcara.kotlinx.locale.datetime.cldr.internal.bundledDateTimeLocales
import dev.carcara.kotlinx.locale.datetime.cldr.internal.formatPattern
import dev.carcara.kotlinx.locale.datetime.cldr.internal.localeDataFor
import dev.carcara.kotlinx.locale.datetime.cldr.internal.parseDateTimePattern
import dev.carcara.kotlinx.locale.datetime.cldr.internal.withoutZoneFields
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * The date and time formats CLDR ships, compiled into this artifact, together
 * with the pattern parser and formatter that render them.
 *
 * Every lookup answers, because CLDR root carries a complete set of patterns
 * and names, so the returns narrow to non-null.
 */
public object CldrDateTime : DateTimeFormatSource {

    override val supportedLocales: Set<Locale>
        get() = bundledDateTimeLocales

    override fun formatDateOrNull(date: LocalDate, style: FormatStyle, locale: Locale): String {
        val data = localeDataFor(locale)
        val tokens = parseDateTimePattern(data.dateFormats[style.ordinal])
        return formatPattern(tokens, data, date = date, time = null)
    }

    /**
     * Time-zone fields present in the FULL and LONG patterns are omitted, since
     * a [LocalTime] carries no zone information.
     */
    override fun formatTimeOrNull(time: LocalTime, style: FormatStyle, locale: Locale): String {
        val data = localeDataFor(locale)
        val tokens = parseDateTimePattern(data.timeFormats[style.ordinal]).withoutZoneFields()
        return formatPattern(tokens, data, date = null, time = time)
    }

    /**
     * Combines the locale's date and time patterns with its CLDR "glue" pattern,
     * e.g. `Sunday, July 27, 2026, 3:05 PM` for `en`.
     */
    override fun formatDateTimeOrNull(dateTime: LocalDateTime, dateStyle: FormatStyle, timeStyle: FormatStyle, locale: Locale): String {
        val data = localeDataFor(locale)
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
    override fun monthNameOrNull(month: Int, style: TextStyle, locale: Locale): String? {
        val data = localeDataFor(locale)
        val index = month - 1
        if (index !in data.monthsWide.indices) return null
        return when (style) {
            TextStyle.FULL -> data.monthsWide[index]
            TextStyle.ABBREVIATED -> data.monthsAbbr[index]
            TextStyle.NARROW -> data.monthsNarrow[index]
        }
    }

    /** The name in the CLDR "format" context, e.g. `segunda-feira`. */
    override fun dayOfWeekNameOrNull(isoDayNumber: Int, style: TextStyle, locale: Locale): String? {
        val data = localeDataFor(locale)
        val index = isoDayNumber - 1
        if (index !in data.daysWide.indices) return null
        return when (style) {
            TextStyle.FULL -> data.daysWide[index]
            TextStyle.ABBREVIATED -> data.daysAbbr[index]
            TextStyle.NARROW -> data.daysNarrow[index]
        }
    }
}
