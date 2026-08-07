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
 * Where a name is about to be shown, which is what CLDR's `contextTransforms`
 * data is keyed on.
 *
 * CLDR stores a name as the language writes it in running text, which in many
 * languages is lower case: `čeština`, `hrvatski`, `července`. Whether a picker
 * row or a calendar header capitalizes that is a property of the language, not
 * of the UI, and CLDR records it per usage.
 *
 * Only [STANDALONE] and [UI_LIST_OR_MENU] carry data; CLDR declares no transform
 * for the middle of a sentence, which is the form the name is already in. ICU
 * models the same thing as `DisplayContext`.
 */
public enum class Capitalization {

    /** The name as CLDR stores it, which is the running-text form. */
    MIDDLE_OF_SENTENCE,

    /** On its own, as a heading or a label. */
    STANDALONE,

    /** In a list or a menu, which is the picker row case. */
    UI_LIST_OR_MENU,
}

/**
 * Applies CLDR's title-case-first-word transform to [text].
 *
 * First word rather than every word: that is the only transform value CLDR uses,
 * across 188 declarations and thirty locale files.
 *
 * Turkish and Azerbaijani are the reason this is not a call to
 * [Char.titlecaseChar]. Kotlin's is locale-invariant, so it maps `i` to `I`,
 * where both of those languages capitalize it to `İ` and the plain answer is
 * visibly wrong in exactly the two languages that ask for the transform most.
 */
@InternalKotlinxLocaleApi
public fun titlecaseFirstWord(text: String, language: String): String {
    if (text.isEmpty()) return text
    val first = text[0]
    val replacement = when {
        first != 'i' -> first.titlecaseChar()
        // tr and az, plus the Latin-script variants that inherit from them.
        language == "tr" || language == "az" -> 'İ'
        else -> first.titlecaseChar()
    }
    if (replacement == first) return text
    return replacement + text.substring(1)
}
