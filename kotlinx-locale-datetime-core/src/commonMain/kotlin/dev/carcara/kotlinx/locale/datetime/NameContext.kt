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

/**
 * Which of CLDR's two naming contexts a month, weekday or quarter name is wanted
 * in.
 *
 * [FORMAT] is the form that goes inside a date, and [STANDALONE] the form that
 * stands on its own: a calendar column header, a month picker, a chart axis. In
 * many languages the two differ by grammatical case — Czech July is `července`
 * in a date and `červenec` alone, Croatian `srpnja` and `srpanj` — and it is not
 * only case. Croatian writes its stand-alone narrow months as `7.`, a number.
 *
 * 283 of CLDR 48.2's 1122 locales distinguish the two somewhere. The other 838
 * answer identically, which is what a source with no stand-alone table falls
 * back to and what CLDR root's own alias says.
 *
 * This is a second axis rather than more [TextStyle] entries because CLDR models
 * it as one: context times width. Collapsing them would give six constants now
 * and eight when the short weekday width lands, and adding an entry to a public
 * enum breaks every exhaustive `when` a consumer wrote.
 */
public enum class NameContext {
    FORMAT,
    STANDALONE,
    ;

    public companion object
}
