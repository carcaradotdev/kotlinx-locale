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

import dev.carcara.kotlinx.locale.Capitalization
import dev.carcara.kotlinx.locale.Locale

/**
 * Which kind of name is being capitalized, since CLDR records the answer per
 * usage rather than per locale.
 *
 * A language can title-case a month in a menu and leave a weekday alone, and
 * several do.
 */
public enum class CalendarNameUsage {
    MONTH_FORMAT,
    MONTH_STANDALONE,
    DAY_FORMAT,
    DAY_STANDALONE,
    RELATIVE,
    ;

    public companion object
}

/**
 * A source that knows how a locale capitalizes a name it is about to show.
 *
 * Its own step rather than a parameter on every lookup. CLDR's contract for a
 * name is what the language writes in running text, and capitalizing it for a
 * heading is a second, separate question; folding the two together would put a
 * third axis on a call that already has a width and a context.
 */
public interface CalendarCapitalizationSource {

    /**
     * [name] capitalized the way [locale] capitalizes a [usage] name shown in
     * [capitalization], which for most locales is [name] unchanged.
     *
     * Thirty of CLDR 48.2's 1122 locale files ask for a transform here. The
     * other 1092 do not, and a locale that writes its month names in lower case
     * without declaring one means it: Belarusian `студзеня` stays `студзеня` in
     * a menu.
     */
    public fun capitalized(name: String, usage: CalendarNameUsage, capitalization: Capitalization, locale: Locale): String = name

    public companion object
}
