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

@file:OptIn(ExperimentalForeignApi::class)

package dev.carcara.kotlinx.locale.datetime.platform

import dev.carcara.kotlinx.locale.datetime.FormatStyle
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

/**
 * The half of the Apple date formatting that names `NSInteger`-backed types.
 *
 * Identical in `appleIlp32Main` and `appleLp64Main`, and it has to be: the types
 * it mentions are 32 bits wide on watchosArm32 and watchosArm64 and 64 everywhere else, so
 * one copy cannot compile for both. See the expects in `appleMain`.
 */
private fun FormatStyle?.toFoundation(): NSDateFormatterStyle = when (this) {
    null -> NSDateFormatterNoStyle
    FormatStyle.FULL -> NSDateFormatterFullStyle
    FormatStyle.LONG -> NSDateFormatterLongStyle
    FormatStyle.MEDIUM -> NSDateFormatterMediumStyle
    FormatStyle.SHORT -> NSDateFormatterShortStyle
}

internal actual fun utcDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): NSDate? {
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

internal actual fun formatUtc(localeTag: String, date: NSDate, dateStyle: FormatStyle?, timeStyle: FormatStyle?): String? =
    NSDateFormatter().apply {
        setLocale(NSLocale(localeIdentifier = localeTag))
        setDateStyle(dateStyle.toFoundation())
        setTimeStyle(timeStyle.toFoundation())
        utcTimeZone()?.let { setTimeZone(it) }
    }.stringFromDate(date)
