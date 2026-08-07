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

package dev.carcara.kotlinx.locale.catalog

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleRef
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue
import dev.carcara.kotlinx.locale.toLocale

val LocaleCatalogTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("theLanguageItselfIsTheBareLocale") {
        assertEquals("pt", PT.tag)
        assertEquals("en", EN.tag)
        // A language CLDR has no regional data for still names itself.
        assertEquals("no", NO.tag)
        assertTrue(NO.entries.isEmpty(), "expected no regional entries under no, got ${NO.entries}")
    }

    test("namesTheRegionsBelowTheLanguage") {
        assertEquals("pt-BR", PT.BR.tag)
        assertEquals("en-GB", EN.GB.tag)
        // pt-PT collides with the enum's own name and still resolves: the
        // language is the companion, the region is the entry.
        assertEquals("pt-PT", PT.PT.tag)
    }

    test("flattensScriptsAndVariantsIntoOneLevel") {
        assertEquals("zh-Hans-CN", ZH.HANS_CN.tag)
        assertEquals("sr-Cyrl-BA", SR.CYRL_BA.tag)
        assertEquals("ca-ES-valencia", CA.ES_VALENCIA.tag)
    }

    test("namesMacroregionsAfterTheirEnglishRegionName") {
        // 001, 150 and 419 are not valid Kotlin identifiers, and backticking
        // them would produce JVM field names Java callers cannot reference.
        assertEquals("ar-001", AR.WORLD.tag)
        assertEquals("en-150", EN.EUROPE.tag)
        assertEquals("es-419", ES.LATIN_AMERICA.tag)
    }

    test("everyTagParsesBackToTheLocaleItNames") {
        val refs = listOf<LocaleRef>(PT, ZH, ES, CA) + PT.entries + ZH.entries + ES.entries + CA.entries
        for (ref in refs) {
            assertEquals(ref.tag, ref.toLocale().toLanguageTag(), "${ref.tag} did not round trip")
        }
    }

    test("entriesCoverTheLanguage") {
        assertTrue(PT.entries.size > 10, "expected every Portuguese region, got ${PT.entries.size}")
        assertTrue(EN.entries.size > 100, "expected every English region, got ${EN.entries.size}")
    }

    test("refsAreUsableWhereverALocaleRefIsAsked") {
        fun tagOf(ref: LocaleRef) = ref.tag
        assertEquals("pt", tagOf(PT))
        assertEquals("pt-BR", tagOf(PT.BR))
        assertEquals(Locale.forLanguageTag("pt-BR"), PT.BR.toLocale())
        assertEquals(Locale.forLanguageTag("pt"), PT.toLocale())
    }
}
