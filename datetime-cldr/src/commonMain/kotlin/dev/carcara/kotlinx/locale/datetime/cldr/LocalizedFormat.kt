package dev.carcara.kotlinx.locale.datetime.cldr

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.TextStyle
import dev.carcara.kotlinx.locale.datetime.displayName
import dev.carcara.kotlinx.locale.datetime.format
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * Formats this date using the locale's CLDR standard date pattern of the given [style].
 *
 * ```
 * LocalDate(2026, 7, 27).format(FormatStyle.LONG, Locale.forLanguageTag("pt-BR"))
 * // "27 de julho de 2026"
 * ```
 */
public fun LocalDate.format(style: FormatStyle, locale: Locale): String = CldrDateTime.format(this, style, locale)

/**
 * Formats this time using the locale's CLDR standard time pattern of the given [style].
 *
 * Time-zone fields present in the FULL and LONG patterns are omitted, since a
 * [LocalTime] carries no zone information.
 */
public fun LocalTime.format(style: FormatStyle, locale: Locale): String = CldrDateTime.format(this, style, locale)

/**
 * Formats this date-time by combining the locale's date and time patterns with
 * its CLDR "glue" pattern (e.g. `Sunday, July 27, 2026, 3:05 PM` for `en`).
 */
public fun LocalDateTime.format(dateStyle: FormatStyle, timeStyle: FormatStyle, locale: Locale): String =
    CldrDateTime.format(this, dateStyle, timeStyle, locale)

/** Formats this date-time using the same [style] for the date and time parts. */
public fun LocalDateTime.format(style: FormatStyle, locale: Locale): String = format(style, style, locale)

/** The localized name of this month in the CLDR "format" context, e.g. `julho`. */
public fun Month.displayName(style: TextStyle, locale: Locale): String = CldrDateTime.displayName(this, style, locale)

/** The localized name of this day of week in the CLDR "format" context, e.g. `segunda-feira`. */
public fun DayOfWeek.displayName(style: TextStyle, locale: Locale): String = CldrDateTime.displayName(this, style, locale)
