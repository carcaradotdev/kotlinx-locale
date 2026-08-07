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

package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.Locale

/**
 * Which components a duration pattern spells out.
 *
 * These are the only three CLDR names, and it offers no way to ask for anything
 * else. The order matches the encoded record.
 */
public enum class DurationStyle {
    /** `h:mm`. */
    HOUR_MINUTE,

    /** `h:mm:ss`. */
    HOUR_MINUTE_SECOND,

    /** `m:ss`, the form an elapsed time under an hour takes. */
    MINUTE_SECOND,
    ;

    public companion object
}

/**
 * A source of CLDR's `durationUnit` patterns.
 *
 * Deliberately a pattern accessor rather than a formatter, and worth saying why,
 * because the gap between the two is where the interesting decision lives.
 *
 * A formatter would have to decide whether 3660 seconds reads as `1:01` or
 * `1:01:00` or `1 hr 1 min`. CLDR does not answer that, and neither do ECMA-402
 * or ICU, all of which take the components from the caller. This library already
 * declines the same question for relative time, where the caller picks the unit.
 * Handing back the pattern keeps that decision where the data leaves it.
 *
 * Be aware how little this data varies. CLDR 48.2 ships `h:mm`, `h:mm:ss` and
 * `m:ss` in root, and across every locale in the release only Finnish and Danish
 * override, both swapping the colon for a full stop. What genuinely varies in a
 * rendered duration is the digits, and those come from the numbering system the
 * rest of this library already resolves.
 *
 * This is not the measurement-unit duration data. `1 hour 5 minutes`, with its
 * plural forms and list patterns, is a separate CLDR table that this library does
 * not carry.
 */
public interface DurationPatternSource {

    /** The pattern for [style], or null when this build carries none for [locale]. */
    public fun durationPatternOrNull(style: DurationStyle, locale: Locale): String?

    public companion object
}

/**
 * The pattern for [style], falling back to root's: `h:mm`, `h:mm:ss` and `m:ss`.
 *
 * The fallback is what almost every locale inherits anyway, so this differs from
 * the exact answer only for the two Nordic locales that write a full stop.
 */
public fun DurationPatternSource.durationPattern(style: DurationStyle, locale: Locale): String =
    durationPatternOrNull(style, locale) ?: ROOT_DURATION_PATTERNS[style.ordinal]

/** Root's `durationUnit` patterns, in [DurationStyle] order. */
internal val ROOT_DURATION_PATTERNS: List<String> = listOf("h:mm", "h:mm:ss", "m:ss")
