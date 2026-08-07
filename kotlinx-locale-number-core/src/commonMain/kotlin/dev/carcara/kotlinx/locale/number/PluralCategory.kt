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

package dev.carcara.kotlinx.locale.number

/**
 * The six plural categories of UTS #35.
 *
 * Not every locale uses all six, and most use two. The names are grammatical
 * labels rather than counts: Czech `few` covers 2 to 4, and its `many` covers
 * every value written with a fraction digit, including `1.0`.
 */
public enum class PluralCategory {
    ZERO,
    ONE,
    TWO,
    FEW,
    MANY,
    OTHER,
    ;

    /** The CLDR spelling, which is what the rule tables are keyed by. */
    public val cldrName: String get() = name.lowercase()

    public companion object {

        /** The category CLDR spells [name], or `null`. */
        public fun forCldrNameOrNull(name: String): PluralCategory? = when (name) {
            "zero" -> ZERO
            "one" -> ONE
            "two" -> TWO
            "few" -> FEW
            "many" -> MANY
            "other" -> OTHER
            else -> null
        }
    }
}

/**
 * Cardinal (`3 files`) or ordinal (`3rd file`).
 *
 * CLDR keeps separate rules for the two, in `plurals.xml` and `ordinals.xml`,
 * and they disagree constantly: English cardinal has `one` and `other` while
 * English ordinal has `one`, `two`, `few` and `other`, which is where `1st`,
 * `2nd`, `3rd` and `4th` come from.
 */
public enum class PluralType {
    CARDINAL,
    ORDINAL,
    ;

    public companion object
}
