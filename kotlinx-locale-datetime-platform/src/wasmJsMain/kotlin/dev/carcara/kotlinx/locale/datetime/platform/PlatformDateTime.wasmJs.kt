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

// Kotlin/Wasm does let a js body see the enclosing function's parameters, which
// is why these are strings here and external declarations on Kotlin/JS.

private fun FormatStyle.wire(): String = when (this) {
    FormatStyle.FULL -> "full"
    FormatStyle.LONG -> "long"
    FormatStyle.MEDIUM -> "medium"
    FormatStyle.SHORT -> "short"
}

private fun TextStyle.wire(): String = when (this) {
    TextStyle.FULL -> "long"
    TextStyle.ABBREVIATED -> "short"
    TextStyle.NARROW -> "narrow"
}

/** UTC throughout: the fields that go in are the fields that come out. */
private fun intlFormat(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int,
    localeTag: String,
    dateStyle: String?,
    timeStyle: String?,
    monthWidth: String?,
    weekdayWidth: String?,
): String? = js(
    "(function(){try{" +
        "var o={timeZone:'UTC'};" +
        "if(dateStyle)o.dateStyle=dateStyle;" +
        "if(timeStyle)o.timeStyle=timeStyle;" +
        "if(monthWidth)o.month=monthWidth;" +
        "if(weekdayWidth)o.weekday=weekdayWidth;" +
        "return new Intl.DateTimeFormat([localeTag],o)" +
        ".format(new Date(Date.UTC(year,month-1,day,hour,minute,second)))" +
        "}catch(e){return null}})()",
)

internal actual fun platformFormatDate(year: Int, month: Int, day: Int, style: FormatStyle, localeTag: String): String? =
    intlFormat(year, month, day, 0, 0, 0, localeTag, style.wire(), null, null, null)

internal actual fun platformFormatTime(hour: Int, minute: Int, second: Int, style: FormatStyle, localeTag: String): String? =
    intlFormat(1970, 1, 1, hour, minute, second, localeTag, null, style.wire(), null, null)

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
): String? = intlFormat(year, month, day, hour, minute, second, localeTag, dateStyle.wire(), timeStyle.wire(), null, null)

internal actual fun platformMonthName(month: Int, width: TextStyle, localeTag: String): String? =
    intlFormat(2024, month, 15, 0, 0, 0, localeTag, null, null, width.wire(), null)

internal actual fun platformDayOfWeekName(isoDayNumber: Int, width: TextStyle, localeTag: String): String? =
    // 2024-01-01 was a Monday, so ISO day n is that date plus n - 1.
    intlFormat(2024, 1, isoDayNumber, 0, 0, 0, localeTag, null, null, null, width.wire())
