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

import platform.Foundation.NSCurrentLocaleDidChangeNotification
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages
import kotlin.concurrent.Volatile

internal actual fun platformSystemLocaleTag(): String? = (NSLocale.preferredLanguages.firstOrNull() as? String)
    ?: NSLocale.currentLocale.localeIdentifier

internal actual fun platformHourCycleKeyword(): String? = observedHourCycle.current()

private val observedHourCycle = ObservedHourCycle()

/**
 * What Foundation says about the hour cycle, held for the process and dropped
 * when Foundation says the current locale changed.
 *
 * Reading it crosses into ICU, and [Locale.current] is the default argument of
 * every formatting call in this library, so an uncached read would put ICU on
 * the path of every format. The cache is not keyed on the locale identifier:
 * the identifier does not change when the 12/24-hour setting does, measured on
 * iOS 26.5 and macOS 26, so a key like that would go stale exactly when the
 * setting flips.
 */
private class ObservedHourCycle {

    @Volatile
    private var cached: String? = null

    @Volatile
    private var stale: Boolean = true

    init {
        NSNotificationCenter.defaultCenter().addObserverForName(NSCurrentLocaleDidChangeNotification, null, null) {
            stale = true
        }
    }

    fun current(): String? {
        if (stale) {
            stale = false
            cached = read()
        }
        return cached
    }

    private fun read(): String? = keywordFromIdentifier() ?: keywordFromTimePreference()
}

private fun keywordFromIdentifier(): String? = Locale
    .forLanguageTagOrNull(NSLocale.currentLocale.localeIdentifier)
    ?.unicodeKeywordOrNull("hc")

/**
 * The 12/24-hour preference behind Settings, read the way Foundation exposes it:
 * the hour field of the `j` skeleton. The identifier carries no `hc` keyword for
 * it, measured on iOS 26.5 and macOS 26.
 */
private fun keywordFromTimePreference(): String? = timePatternFromSkeleton(HOUR_SKELETON)
    ?.let(::hourCycleFromTimePattern)

/**
 * The current locale's time pattern for a CLDR skeleton, as Foundation resolves
 * it.
 *
 * An expect rather than a function here because the template call takes an
 * options argument typed `NSUInteger`, which is 32 bits wide on watchosArm32 and
 * watchosArm64 and 64 elsewhere, and Kotlin refuses a type of varying width in a
 * source set spanning both. Only this declaration is written twice; the pattern
 * comes back as a plain String and everything read off it stays shared.
 */
internal expect fun timePatternFromSkeleton(skeleton: String): String?

private const val HOUR_SKELETON = "j"
