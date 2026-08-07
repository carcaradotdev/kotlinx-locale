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
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek as JavaDayOfWeek
import java.time.LocalDate as JavaLocalDate
import java.time.LocalDateTime as JavaLocalDateTime
import java.time.LocalTime as JavaLocalTime
import java.time.Month as JavaMonth
import java.time.format.FormatStyle as JavaFormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale as JavaLocale

// The four lengths and the three widths map one to one, so nothing is emulated.
private fun FormatStyle.toJava(): JavaFormatStyle = when (this) {
    FormatStyle.FULL -> JavaFormatStyle.FULL
    FormatStyle.LONG -> JavaFormatStyle.LONG
    FormatStyle.MEDIUM -> JavaFormatStyle.MEDIUM
    FormatStyle.SHORT -> JavaFormatStyle.SHORT
}

private fun TextStyle.toJava(): JavaTextStyle = when (this) {
    TextStyle.FULL -> JavaTextStyle.FULL
    TextStyle.ABBREVIATED -> JavaTextStyle.SHORT
    TextStyle.NARROW -> JavaTextStyle.NARROW
}

/**
 * FULL and LONG date and time patterns carry zone fields, and a LocalDate or
 * LocalTime has no zone to fill them with. Formatting in UTC gives the fields a
 * value without shifting the date, which is what the host zone would do.
 */
private fun formatterFor(style: FormatStyle, localeTag: String, dateOnly: Boolean): DateTimeFormatter {
    val base = if (dateOnly) {
        DateTimeFormatter.ofLocalizedDate(style.toJava())
    } else {
        DateTimeFormatter.ofLocalizedTime(style.toJava())
    }
    return base.withLocale(JavaLocale.forLanguageTag(localeTag)).withZone(java.time.ZoneOffset.UTC)
}

internal actual fun platformFormatDate(year: Int, month: Int, day: Int, style: FormatStyle, localeTag: String): String? = try {
    formatterFor(style, localeTag, dateOnly = true)
        .format(JavaLocalDate.of(year, month, day).atStartOfDay(java.time.ZoneOffset.UTC))
} catch (_: RuntimeException) {
    null
}

internal actual fun platformFormatTime(hour: Int, minute: Int, second: Int, style: FormatStyle, localeTag: String): String? = try {
    formatterFor(style, localeTag, dateOnly = false)
        .format(JavaLocalTime.of(hour, minute, second).atDate(JavaLocalDate.of(1970, 1, 1)).atZone(java.time.ZoneOffset.UTC))
} catch (_: RuntimeException) {
    null
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
): String? = try {
    DateTimeFormatter.ofLocalizedDateTime(dateStyle.toJava(), timeStyle.toJava())
        .withLocale(JavaLocale.forLanguageTag(localeTag))
        .withZone(java.time.ZoneOffset.UTC)
        .format(JavaLocalDateTime.of(year, month, day, hour, minute, second).atZone(java.time.ZoneOffset.UTC))
} catch (_: RuntimeException) {
    null
}

internal actual fun platformMonthName(month: Int, width: TextStyle, localeTag: String): String? = try {
    JavaMonth.of(month).getDisplayName(width.toJava(), JavaLocale.forLanguageTag(localeTag))
} catch (_: RuntimeException) {
    null
}

internal actual fun platformDayOfWeekName(isoDayNumber: Int, width: TextStyle, localeTag: String): String? = try {
    JavaDayOfWeek.of(isoDayNumber).getDisplayName(width.toJava(), JavaLocale.forLanguageTag(localeTag))
} catch (_: RuntimeException) {
    null
}
