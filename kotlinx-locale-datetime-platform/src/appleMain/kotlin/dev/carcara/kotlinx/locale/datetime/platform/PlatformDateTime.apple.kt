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

package dev.carcara.kotlinx.locale.datetime.platform

import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.TextStyle
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithAbbreviation

/**
 * Builds the instant in UTC, so the fields that go in are the fields that come
 * out. Left to the device's zone a date would print as the day before or after
 * for anyone west or east of it.
 *
 * An expect rather than a function here because `NSDateComponents` takes
 * `NSInteger`, which is 32 bits wide on watchosArm32 and watchosArm64 and 64 elsewhere,
 * and Kotlin refuses a type of varying width in a source set spanning both.
 * Only the declarations that name one are written twice; everything else in this
 * file is shared.
 */
internal expect fun utcDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): NSDate?

/**
 * Formats with the locale's own patterns for the given styles, in UTC.
 *
 * A null style means the part is left out, which is Foundation's `NoStyle`. The
 * styles cross this boundary as [FormatStyle] rather than as
 * `NSDateFormatterStyle` for the reason given on [utcDate].
 */
internal expect fun formatUtc(localeTag: String, date: NSDate, dateStyle: FormatStyle?, timeStyle: FormatStyle?): String?

internal fun utcTimeZone(): NSTimeZone? = NSTimeZone.timeZoneWithAbbreviation("UTC")

internal actual fun platformFormatDate(year: Int, month: Int, day: Int, style: FormatStyle, localeTag: String): String? {
    val date = utcDate(year, month, day, 0, 0, 0) ?: return null
    return formatUtc(localeTag, date, style, null)
}

internal actual fun platformFormatTime(hour: Int, minute: Int, second: Int, style: FormatStyle, localeTag: String): String? {
    val date = utcDate(1970, 1, 1, hour, minute, second) ?: return null
    return formatUtc(localeTag, date, null, style)
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
    return formatUtc(localeTag, date, dateStyle, timeStyle)
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
