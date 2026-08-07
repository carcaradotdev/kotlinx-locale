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

// This target's platform exposes no locale data Kotlin can read, so every call
// misses and a consumer composes with a bundled source.

internal actual fun platformFormatDate(year: Int, month: Int, day: Int, style: FormatStyle, localeTag: String): String? = null

internal actual fun platformFormatTime(hour: Int, minute: Int, second: Int, style: FormatStyle, localeTag: String): String? = null

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
): String? = null

internal actual fun platformMonthName(month: Int, width: TextStyle, localeTag: String): String? = null

internal actual fun platformDayOfWeekName(isoDayNumber: Int, width: TextStyle, localeTag: String): String? = null
