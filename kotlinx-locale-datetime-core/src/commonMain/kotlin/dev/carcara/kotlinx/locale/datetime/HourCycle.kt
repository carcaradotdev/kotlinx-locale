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

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale

/**
 * The hour cycle a time is written in, as the `hc` key of UTS #35 defines it.
 *
 * [C12] and [C24] are the standard's Technical Preview values and are what a
 * device 12/24-hour toggle maps to: they resolve to a concrete cycle against the
 * locale's own `allowed` list, which is why they carry no pattern letter here.
 */
public enum class HourCycle(internal val keyword: String, patternLetter: Char?) {
    H11("h11", 'K'),
    H12("h12", 'h'),
    H23("h23", 'H'),
    H24("h24", 'k'),
    C12("c12", null),
    C24("c24", null),
    ;

    /** The CLDR pattern letter this cycle writes the hour with; null for [C12] and [C24]. */
    @InternalKotlinxLocaleApi
    public val patternLetter: Char? = patternLetter

    public companion object {
        internal fun forKeywordOrNull(keyword: String): HourCycle? = entries.firstOrNull { it.keyword == keyword }
    }
}

/** The cycle [this] names, or `null` when it names none. */
public val Locale.hourCycle: HourCycle?
    get() = unicodeKeywordOrNull("hc")?.let(HourCycle::forKeywordOrNull)

/** [this] with [cycle] named, or with the `hc` key removed when [cycle] is `null`. */
public fun Locale.withHourCycle(cycle: HourCycle?): Locale = withUnicodeKeyword("hc", cycle?.keyword)
