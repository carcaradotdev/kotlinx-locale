@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.datetime.platform

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.DateTimeFormatSource
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.TextStyle
import dev.carcara.kotlinx.locale.datetime.displayName
import dev.carcara.kotlinx.locale.datetime.format
import dev.carcara.kotlinx.locale.platform.PlatformLocaleData
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

/**
 * Every actual formats in UTC.
 *
 * A [LocalDate] carries no zone, and the platform formatters all take an instant
 * plus a zone. Left to the host's zone, a date would render as the day before or
 * after for anyone west or east of it, which is a bug that only shows up for some
 * users in some timezones. Fixing the zone to UTC makes the printed fields the
 * fields that were passed in.
 */
internal expect fun platformFormatDate(year: Int, month: Int, day: Int, style: FormatStyle, localeTag: String): String?

internal expect fun platformFormatTime(hour: Int, minute: Int, second: Int, style: FormatStyle, localeTag: String): String?

internal expect fun platformFormatDateTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int,
    dateStyle: FormatStyle,
    timeStyle: FormatStyle,
    localeTag: String,
): String?

internal expect fun platformMonthName(month: Int, width: TextStyle, localeTag: String): String?

internal expect fun platformDayOfWeekName(isoDayNumber: Int, width: TextStyle, localeTag: String): String?

/**
 * Dates, times and calendar names from the host platform:
 * `java.time.format.DateTimeFormatter` on JVM and Android, `Intl.DateTimeFormat`
 * on JS and Wasm/JS, `NSDateFormatter` on Apple.
 *
 * This is the domain where using the host pays best. The bundled CLDR patterns
 * are the largest part of the datetime artifact, and a platform source replaces
 * all of them: the four standard lengths, the glue that joins a date to a time,
 * and the month and weekday names in three widths.
 *
 * It is also the domain where the shapes agree most closely. The four
 * [FormatStyle] lengths map one to one onto `java.time`'s FormatStyle, `Intl`'s
 * `dateStyle` and `NSDateFormatterStyle`, and the three [TextStyle] widths onto
 * their name widths. Nothing is being emulated here.
 *
 * Linux, Windows, Android Native and WASI expose no locale data Kotlin can read,
 * so on those four every call misses and a consumer composes:
 *
 * ```
 * val dates = FallbackDateTimeFormats(primary = PlatformDateTime, fallback = CldrDateTime)
 * ```
 *
 * Everything is formatted in UTC, because a [LocalDate] has no zone and the host
 * zone would shift the printed day for some users.
 */
public object PlatformDateTime : DateTimeFormatSource {

    override val supportedLocales: Set<Locale> by lazy {
        PlatformLocaleData.availableLocaleTags()
            .mapNotNullTo(LinkedHashSet()) { Locale.forLanguageTagOrNull(it) }
    }

    /** False on the targets whose platform exposes no locale data at all. */
    public val isAvailable: Boolean
        get() = PlatformLocaleData.isAvailable

    override fun formatDateOrNull(date: LocalDate, style: FormatStyle, locale: Locale): String? =
        platformFormatDate(date.year, date.month.number, date.day, style, locale.toLanguageTag())

    override fun formatTimeOrNull(time: LocalTime, style: FormatStyle, locale: Locale): String? =
        platformFormatTime(time.hour, time.minute, time.second, style, locale.toLanguageTag())

    override fun formatDateTimeOrNull(dateTime: LocalDateTime, dateStyle: FormatStyle, timeStyle: FormatStyle, locale: Locale): String? =
        platformFormatDateTime(
            year = dateTime.year,
            month = dateTime.month.number,
            day = dateTime.day,
            hour = dateTime.hour,
            minute = dateTime.minute,
            second = dateTime.second,
            dateStyle = dateStyle,
            timeStyle = timeStyle,
            localeTag = locale.toLanguageTag(),
        )

    override fun monthNameOrNull(month: Int, style: TextStyle, locale: Locale): String? {
        if (month !in 1..12) return null
        return platformMonthName(month, style, locale.toLanguageTag())?.takeIf(String::isNotBlank)
    }

    override fun dayOfWeekNameOrNull(isoDayNumber: Int, style: TextStyle, locale: Locale): String? {
        if (isoDayNumber !in 1..7) return null
        return platformDayOfWeekName(isoDayNumber, style, locale.toLanguageTag())?.takeIf(String::isNotBlank)
    }
}

/** Formats this date with the platform's standard date format of the given [style]. */
public fun LocalDate.format(style: FormatStyle, locale: Locale): String = PlatformDateTime.format(this, style, locale)

/** Formats this time with the platform's standard time format of the given [style]. */
public fun LocalTime.format(style: FormatStyle, locale: Locale): String = PlatformDateTime.format(this, style, locale)

/** Formats this date-time with the platform's own joining of a date and a time. */
public fun LocalDateTime.format(dateStyle: FormatStyle, timeStyle: FormatStyle, locale: Locale): String =
    PlatformDateTime.format(this, dateStyle, timeStyle, locale)

/** Formats this date-time using the same [style] for the date and time parts. */
public fun LocalDateTime.format(style: FormatStyle, locale: Locale): String = format(style, style, locale)

/** The platform's name for this month. */
public fun Month.displayName(style: TextStyle, locale: Locale): String = PlatformDateTime.displayName(this, style, locale)

/** The platform's name for this day of week. */
public fun DayOfWeek.displayName(style: TextStyle, locale: Locale): String = PlatformDateTime.displayName(this, style, locale)
