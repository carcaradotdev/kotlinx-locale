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
import kotlin.js.Date

/**
 * External declarations rather than `js("...")` strings, because a string cannot
 * see this file's function parameters on Kotlin/JS.
 */
@JsName("Intl")
private external object Intl {
    class DateTimeFormat(locales: Array<String>, options: dynamic) {
        fun format(date: Date): String
    }
}

/** `Date.UTC` builds the instant without the host zone getting a say. */
@JsName("Date")
private external object JsDate {
    @JsName("UTC")
    fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Double
}

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

private fun utc(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): Date =
    Date(JsDate.utcMillis(year, month - 1, day, hour, minute, second))

private fun format(date: Date, localeTag: String, configure: (dynamic) -> Unit): String? = try {
    // UTC throughout: the fields that go in are the fields that come out.
    val options: dynamic = js("({ timeZone: 'UTC' })")
    configure(options)
    Intl.DateTimeFormat(arrayOf(localeTag), options).format(date)
} catch (_: Throwable) {
    null
}

internal actual fun platformFormatDate(year: Int, month: Int, day: Int, style: FormatStyle, localeTag: String): String? =
    format(utc(year, month, day), localeTag) { it.dateStyle = style.wire() }

internal actual fun platformFormatTime(hour: Int, minute: Int, second: Int, style: FormatStyle, localeTag: String): String? =
    format(utc(1970, 1, 1, hour, minute, second), localeTag) { it.timeStyle = style.wire() }

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
): String? = format(utc(year, month, day, hour, minute, second), localeTag) {
    it.dateStyle = dateStyle.wire()
    it.timeStyle = timeStyle.wire()
}

// Intl has no name tables to read, so a name is whatever it prints for a date
// that falls in the month, or on the weekday, being asked about.
internal actual fun platformMonthName(month: Int, width: TextStyle, localeTag: String): String? =
    format(utc(2024, month, 15), localeTag) { it.month = width.wire() }

internal actual fun platformDayOfWeekName(isoDayNumber: Int, width: TextStyle, localeTag: String): String? =
    // 2024-01-01 was a Monday, so ISO day n is that date plus n - 1.
    format(utc(2024, 1, isoDayNumber), localeTag) { it.weekday = width.wire() }
