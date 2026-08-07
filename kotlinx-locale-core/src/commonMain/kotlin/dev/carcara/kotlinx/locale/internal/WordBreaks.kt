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

package dev.carcara.kotlinx.locale.internal

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

/**
 * The punctuation that UAX #29 keeps inside a word rather than between two.
 *
 * Rules WB6 and WB7 say that a `MidLetter`, a `MidNumLet` or a `Single_Quote`
 * standing between two letters does not end a word. Catalan writes `Gal·la` with
 * a middle dot and expects one initial from it; a rule that treats every
 * non-letter as a separator produces two.
 *
 * Only those three classes, and not the rest of UAX #29's word boundaries. The
 * classes that need a dictionary to apply, which is what the scripts without
 * spaces between words need, are a boundary this library records rather than
 * approximates. See the person name conformance test for the locales that
 * excludes.
 *
 * The set is not written out here. It is the vendored UCD file the generator
 * reads, and that file's release is checked against the one the tables were
 * built from, so the properties cannot drift away from the cases.
 */
@InternalKotlinxLocaleApi
public object WordBreaks {

    private var midLetters = ""

    /**
     * Installs the property set.
     *
     * Called once by whichever generated artifact carries it. Until then no code
     * point is mid-word, which degrades to breaking at every non-letter: the
     * behaviour this had before the rule existed, rather than anything wrong.
     */
    @InternalKotlinxLocaleApi
    public fun install(table: String) {
        if (midLetters.isNotEmpty() || table.isEmpty()) return
        midLetters = table
    }

    /**
     * Whether [ch] is a `MidLetter`, `MidNumLet` or `Single_Quote`.
     *
     * A linear scan rather than a binary search: the set is seventeen code
     * points in Unicode 15.1, all of them BMP, and it is only consulted for a
     * character that already failed the letter test.
     */
    @InternalKotlinxLocaleApi
    public fun isMidWord(ch: Char): Boolean = midLetters.indexOf(ch) >= 0
}
