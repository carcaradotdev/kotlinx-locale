package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.internal.formatPattern
import dev.carcara.kotlinx.locale.datetime.internal.localeDataFor
import dev.carcara.kotlinx.locale.datetime.internal.parseDateTimePattern
import dev.carcara.kotlinx.locale.datetime.internal.withoutZoneFields
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

/** The four CLDR standard format lengths. */
public enum class FormatStyle { FULL, LONG, MEDIUM, SHORT }

/** Width of localized month and day-of-week names. */
public enum class TextStyle { FULL, ABBREVIATED, NARROW }

/**
 * Formats this date using the locale's CLDR standard date pattern of the given [style].
 *
 * ```
 * LocalDate(2026, 7, 27).format(FormatStyle.LONG, Locale.forLanguageTag("pt-BR"))
 * // "27 de julho de 2026"
 * ```
 */
public fun LocalDate.format(style: FormatStyle, locale: Locale): String {
    val data = localeDataFor(locale)
    val tokens = parseDateTimePattern(data.dateFormats[style.ordinal])
    return formatPattern(tokens, data, date = this, time = null)
}

/**
 * Formats this time using the locale's CLDR standard time pattern of the given [style].
 *
 * Time-zone fields present in the FULL and LONG patterns are omitted, since a
 * [LocalTime] carries no zone information.
 */
public fun LocalTime.format(style: FormatStyle, locale: Locale): String {
    val data = localeDataFor(locale)
    val tokens = parseDateTimePattern(data.timeFormats[style.ordinal]).withoutZoneFields()
    return formatPattern(tokens, data, date = null, time = this)
}

/**
 * Formats this date-time by combining the locale's date and time patterns with
 * its CLDR "glue" pattern (e.g. `Sunday, July 27, 2026, 3:05 PM` for `en`).
 */
public fun LocalDateTime.format(dateStyle: FormatStyle, timeStyle: FormatStyle, locale: Locale): String {
    val data = localeDataFor(locale)
    val datePart = formatPattern(
        parseDateTimePattern(data.dateFormats[dateStyle.ordinal]),
        data,
        date = date,
        time = null,
    )
    val timePart = formatPattern(
        parseDateTimePattern(data.timeFormats[timeStyle.ordinal]).withoutZoneFields(),
        data,
        date = null,
        time = time,
    )
    // The glue pattern only contains literals (possibly quoted) and the {1}/{0}
    // placeholders for the date and time parts.
    val glue = formatPattern(
        parseDateTimePattern(data.glueFormats[dateStyle.ordinal]),
        data,
        date = null,
        time = null,
    )
    return glue.replace("{1}", datePart).replace("{0}", timePart)
}

/** Formats this date-time using the same [style] for the date and time parts. */
public fun LocalDateTime.format(style: FormatStyle, locale: Locale): String = format(style, style, locale)

/** The localized name of this month in the CLDR "format" context, e.g. `julho`. */
public fun Month.displayName(style: TextStyle, locale: Locale): String {
    val data = localeDataFor(locale)
    val index = number - 1
    return when (style) {
        TextStyle.FULL -> data.monthsWide[index]
        TextStyle.ABBREVIATED -> data.monthsAbbr[index]
        TextStyle.NARROW -> data.monthsNarrow[index]
    }
}

/** The localized name of this day of week in the CLDR "format" context, e.g. `segunda-feira`. */
public fun DayOfWeek.displayName(style: TextStyle, locale: Locale): String {
    val data = localeDataFor(locale)
    val index = isoDayNumber - 1
    return when (style) {
        TextStyle.FULL -> data.daysWide[index]
        TextStyle.ABBREVIATED -> data.daysAbbr[index]
        TextStyle.NARROW -> data.daysNarrow[index]
    }
}
