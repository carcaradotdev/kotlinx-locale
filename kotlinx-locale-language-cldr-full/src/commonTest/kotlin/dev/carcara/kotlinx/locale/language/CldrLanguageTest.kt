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

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.language.cldr.CldrLanguage
import dev.carcara.kotlinx.locale.language.cldr.displayName
import dev.carcara.kotlinx.locale.language.cldr.nativeDisplayName
import dev.carcara.kotlinx.locale.language.cldr.regionName
import dev.carcara.kotlinx.locale.language.cldr.scriptName
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue

private val EN = Locale.of("en")
private val PT = Locale.of("pt")

val CldrLanguageTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("namesALanguageInAnotherLanguage") {
        assertEquals("German", Locale.of("de").displayName(EN))
        // Portuguese does not capitalize a language name, and CLDR stores what
        // the language writes rather than what a UI might want.
        assertEquals("alemão", Locale.of("de").displayName(PT))
        assertEquals("Czech", Locale.of("cs").displayName(EN))
        assertEquals("Icelandic", Locale.of("is").displayName(EN))
    }

    test("namesALanguageInItself") {
        // The row title of a language picker. CLDR writes these as the language
        // does, which is lower case in several of them.
        assertEquals("Deutsch", Locale.of("de").nativeDisplayName)
        assertEquals("čeština", Locale.of("cs").nativeDisplayName)
        assertEquals("íslenska", Locale.of("is").nativeDisplayName)
        assertEquals("português", Locale.of("pt").nativeDisplayName)
    }

    test("dialectNamesComeFromCldrAndTheStandardFormIsComposed") {
        val british = Locale.forLanguageTag("en-GB")
        assertEquals("British English", british.displayName(EN))
        assertEquals("English (United Kingdom)", british.displayName(EN, LanguageDisplay.STANDARD))
        assertEquals("UK English", british.displayName(EN, style = LanguageNameStyle.SHORT))
        assertEquals("European Portuguese", Locale.forLanguageTag("pt-PT").displayName(EN))
    }

    test("composesTheSubtagsTheDialectNameDidNotConsume") {
        // CLDR has its own dialect name for es-419, so DIALECT takes it and only
        // STANDARD composes. 419 is a macro-region, which the country enum does
        // not carry and this domain's own territory table does.
        assertEquals("Latin American Spanish", Locale.forLanguageTag("es-419").displayName(EN))
        assertEquals(
            "Spanish (Latin America)",
            Locale.forLanguageTag("es-419").displayName(EN, LanguageDisplay.STANDARD),
        )
        assertEquals("Serbian (Cyrillic)", Locale.forLanguageTag("sr-Cyrl").displayName(EN))
        assertEquals(
            "Serbian (Cyrillic, Bosnia & Herzegovina)",
            Locale.forLanguageTag("sr-Cyrl-BA").displayName(EN),
        )
    }

    test("namesScriptsAndRegions") {
        assertEquals("Latin", EN.scriptName("Latn"))
        assertEquals("Cyrillic", EN.scriptName("Cyrl"))
        assertEquals("Latin America", EN.regionName("419"))
        assertEquals("Croatia", EN.regionName("HR"))
    }

    test("anUnknownSubtagFallsBackToItself") {
        assertEquals("qqq", EN.scriptName("qqq"))
        assertEquals("zz", Locale.of("zz").displayName(EN))
    }

    test("everyLocaleCanNameItself") {
        var checked = 0
        for (locale in CldrLanguage.supportedLocales) {
            assertTrue(locale.nativeDisplayName.isNotBlank(), "$locale named nothing")
            assertTrue(locale.displayName(EN).isNotBlank(), "$locale has no English name")
            checked++
        }
        assertTrue(checked > 1000, "expected the full locale set, got $checked")
    }
}
