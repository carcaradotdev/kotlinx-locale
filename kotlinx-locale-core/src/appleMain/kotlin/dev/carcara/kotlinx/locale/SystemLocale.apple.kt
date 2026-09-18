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

import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages

internal actual fun platformSystemLocaleTag(): String? = (NSLocale.preferredLanguages.firstOrNull() as? String)
    ?: NSLocale.currentLocale.localeIdentifier

internal actual fun platformHourCycleKeyword(): String? = keywordFromIdentifier() ?: keywordFromTimePreference()

private fun keywordFromIdentifier(): String? = Locale
    .forLanguageTagOrNull(NSLocale.currentLocale.localeIdentifier)
    ?.unicodeKeywordOrNull("hc")

/**
 * The 12/24-hour preference behind Settings, read the way Foundation exposes it:
 * the hour field of the `j` skeleton. The identifier carries no `hc` keyword for
 * it, measured on iOS 26.5 and macOS 26.
 */
private fun keywordFromTimePreference(): String? {
    val pattern = NSDateFormatter.dateFormatFromTemplate(TIME_SKELETON, 0u, NSLocale.currentLocale) ?: return null
    return when (hourField(pattern)) {
        'H', 'k' -> "c24"
        'h', 'K' -> "c12"
        else -> null
    }
}

private fun hourField(pattern: String): Char? {
    var quoted = false
    for (character in pattern) {
        when {
            character == '\'' -> quoted = !quoted
            !quoted && character in HOUR_FIELDS -> return character
        }
    }
    return null
}

private const val TIME_SKELETON = "j"
private const val HOUR_FIELDS = "HhKk"
