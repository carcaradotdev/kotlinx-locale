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

package dev.carcara.kotlinx.locale.language

import dev.carcara.kotlinx.locale.Capitalization
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.language.cldr.CldrLanguage
import dev.carcara.kotlinx.locale.language.cldr.nativeDisplayName
import kotlin.test.Test
import kotlin.test.assertEquals

private val CS = Locale.of("cs")
private val BE = Locale.of("be")
private val TR = Locale.of("tr")

/**
 * CLDR stores a name as the language writes it in running text. Whether a picker
 * row capitalizes it is a property of the language, recorded per usage, and not
 * something a caller can decide by uppercasing the first letter.
 */
class CapitalizationTest {

    @Test
    fun czechTitleCasesALanguageNameInAMenu() {
        val name = CS.nativeDisplayName
        assertEquals("čeština", name, "CLDR stores the running-text form")
        assertEquals(
            "Čeština",
            CldrLanguage.capitalized(name, LanguageNameUsage.LANGUAGE, Capitalization.UI_LIST_OR_MENU, CS),
        )
        assertEquals(
            name,
            CldrLanguage.capitalized(name, LanguageNameUsage.LANGUAGE, Capitalization.MIDDLE_OF_SENTENCE, CS),
        )
    }

    @Test
    fun aLocaleThatDeclaresNoTransformIsLeftAlone() {
        // Belarusian writes its names in lower case and declares no transform,
        // which means it. Title-casing anyway would be wrong here and in 251
        // other locales, which is why the data has to ship rather than be
        // approximated.
        val name = BE.nativeDisplayName
        assertEquals(
            name,
            CldrLanguage.capitalized(name, LanguageNameUsage.LANGUAGE, Capitalization.UI_LIST_OR_MENU, BE),
        )
    }

    @Test
    fun turkishDeclaresNoTransformForLanguageNames() {
        // It declares one for relative wording and not for names, which is the
        // kind of per-usage difference that makes a single per-locale flag the
        // wrong shape for this data.
        assertEquals(
            "ingilizce",
            CldrLanguage.capitalized("ingilizce", LanguageNameUsage.LANGUAGE, Capitalization.UI_LIST_OR_MENU, TR),
        )
    }
}
