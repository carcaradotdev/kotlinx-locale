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

/**
 * The platform's current locale as a raw tag (BCP 47 or POSIX flavored), or
 * `null` when the platform does not expose one. This is the only expect/actual
 * surface in the library; all parsing and formatting happens in common code.
 */
internal expect fun platformSystemLocaleTag(): String?

/**
 * The hour cycle the platform's user has chosen, as an `hc` keyword value
 * (`h11`, `h12`, `h23`, `h24`, `c12` or `c24`), or `null` when the platform
 * exposes none. A device 12/24-hour toggle maps to `c12` or `c24`, which
 * resolve against the locale's own allowed list.
 */
internal expect fun platformHourCycleKeyword(): String?

/**
 * The `hc` keyword a platform time pattern implies: `c24` where its hour field
 * is `H` or `k`, `c12` where it is `h` or `K`, and `null` where the pattern has
 * no hour field. The concrete cycle stays with the locale's own allowed list.
 *
 * The hour field is not always first: a pattern can open with the day period,
 * and a quoted literal can hold any letter, so quoted runs are skipped. Two
 * quotes in a row are a literal quote, which this reads as a run that opens and
 * closes at once.
 */
internal fun hourCycleFromTimePattern(pattern: String): String? = when (hourField(pattern)) {
    'H', 'k' -> "c24"
    'h', 'K' -> "c12"
    else -> null
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

private const val HOUR_FIELDS = "HhKk"
