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

package dev.carcara.kotlinx.locale

import android.text.format.DateFormat

internal actual fun platformSystemLocaleTag(): String? = java.util.Locale.getDefault().toLanguageTag()

/**
 * Read on every call rather than cached: the setting behind it is
 * `Settings.System.TIME_12_24`, and the user can change it while the app runs.
 */
internal actual fun platformHourCycleKeyword(): String? {
    val context = LocaleContext.applicationContext ?: return null
    return hourCycleKeyword(DateFormat.is24HourFormat(context))
}

/**
 * The cycle family rather than the cycle: `c24` and `c12` resolve against the
 * locale's own allowed list, so Japan still reaches `h11` instead of `h12`.
 */
internal fun hourCycleKeyword(is24Hour: Boolean): String = if (is24Hour) "c24" else "c12"
