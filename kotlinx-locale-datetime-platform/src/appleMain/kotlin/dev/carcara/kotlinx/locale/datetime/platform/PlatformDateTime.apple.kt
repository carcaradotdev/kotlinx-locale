@file:OptIn(ExperimentalForeignApi::class)

package dev.carcara.kotlinx.locale.datetime.platform

import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.TextStyle
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithAbbreviation

private fun FormatStyle.toFoundation(): NSDateFormatterStyle = when (this) {
    FormatStyle.FULL -> NSDateFormatterFullStyle
    FormatStyle.LONG -> NSDateFormatterLongStyle
    FormatStyle.MEDIUM -> NSDateFormatterMediumStyle
    FormatStyle.SHORT -> NSDateFormatterShortStyle
}

private fun utcTimeZone(): NSTimeZone? = NSTimeZone.timeZoneWithAbbreviation("UTC")

/**
 * Builds the instant in UTC, so the fields that go in are the fields that come
 * out. Left to the device's zone a date would print as the day before or after
 * for anyone west or east of it.
 */
private fun utcDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): NSDate? {
    val calendar = NSCalendar(NSCalendarIdentifierGregorian)
    utcTimeZone()?.let { calendar.timeZone = it }
    val components = NSDateComponents().apply {
        setYear(year.convert())
        setMonth(month.convert())
        setDay(day.convert())
        setHour(hour.convert())
        setMinute(minute.convert())
        setSecond(second.convert())
    }
    return calendar.dateFromComponents(components)
}

private fun formatter(localeTag: String, dateStyle: NSDateFormatterStyle, timeStyle: NSDateFormatterStyle): NSDateFormatter =
    NSDateFormatter().apply {
        setLocale(NSLocale(localeIdentifier = localeTag))
        setDateStyle(dateStyle)
        setTimeStyle(timeStyle)
        utcTimeZone()?.let { setTimeZone(it) }
    }

internal actual fun platformFormatDate(year: Int, month: Int, day: Int, style: FormatStyle, localeTag: String): String? {
    val date = utcDate(year, month, day, 0, 0, 0) ?: return null
    return formatter(localeTag, style.toFoundation(), NSDateFormatterNoStyle).stringFromDate(date)
}

internal actual fun platformFormatTime(hour: Int, minute: Int, second: Int, style: FormatStyle, localeTag: String): String? {
    val date = utcDate(1970, 1, 1, hour, minute, second) ?: return null
    return formatter(localeTag, NSDateFormatterNoStyle, style.toFoundation()).stringFromDate(date)
}

internal actual fun platformFormatDateTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int,
    dateStyle: FormatStyle,
    timeStyle: FormatStyle,
    localeTag: String,
): String? {
    val date = utcDate(year, month, day, hour, minute, second) ?: return null
    return formatter(localeTag, dateStyle.toFoundation(), timeStyle.toFoundation()).stringFromDate(date)
}

/** Foundation hands out the name tables directly, so no date has to be formatted. */
internal actual fun platformMonthName(month: Int, width: TextStyle, localeTag: String): String? {
    val symbols = NSDateFormatter().apply { setLocale(NSLocale(localeIdentifier = localeTag)) }.let {
        when (width) {
            TextStyle.FULL -> it.monthSymbols
            TextStyle.ABBREVIATED -> it.shortMonthSymbols
            TextStyle.NARROW -> it.veryShortMonthSymbols
        }
    }
    return symbols?.getOrNull(month - 1) as? String
}

internal actual fun platformDayOfWeekName(isoDayNumber: Int, width: TextStyle, localeTag: String): String? {
    val symbols = NSDateFormatter().apply { setLocale(NSLocale(localeIdentifier = localeTag)) }.let {
        when (width) {
            TextStyle.FULL -> it.weekdaySymbols
            TextStyle.ABBREVIATED -> it.shortWeekdaySymbols
            TextStyle.NARROW -> it.veryShortWeekdaySymbols
        }
    }
    // Foundation indexes weekdays from Sunday, ISO counts from Monday, so ISO 7
    // (Sunday) is index 0 and ISO 1 (Monday) is index 1.
    return symbols?.getOrNull(isoDayNumber % 7) as? String
}
