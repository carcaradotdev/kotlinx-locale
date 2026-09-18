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

@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertFailsWith
import dev.carcara.kotlinx.locale.test.assertNull
import dev.carcara.kotlinx.locale.test.assertTrue

val LocaleTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("parsesBcp47Tags") {
        val locale = Locale.forLanguageTag("pt-BR")
        assertEquals("pt", locale.language)
        assertEquals("BR", locale.region)
        assertNull(locale.script)
        assertEquals("pt-BR", locale.toLanguageTag())
    }

    test("parsesPosixIdentifiers") {
        val locale = Locale.forLanguageTag("PT_br.UTF-8@latin")
        assertEquals("pt", locale.language)
        assertEquals("BR", locale.region)
        assertEquals("pt-BR", locale.toLanguageTag())
    }

    test("parsesScriptAndRegion") {
        val locale = Locale.forLanguageTag("sr-Cyrl-BA")
        assertEquals("sr", locale.language)
        assertEquals("Cyrl", locale.script)
        assertEquals("BA", locale.region)
        assertEquals("sr-Cyrl-BA", locale.toLanguageTag())
    }

    test("parsesVariants") {
        val locale = Locale.forLanguageTag("ca-ES-VALENCIA")
        assertEquals("valencia", locale.variant)
        assertEquals("ca-ES-valencia", locale.toLanguageTag())
    }

    test("keepsUnicodeExtensions") {
        val locale = Locale.forLanguageTag("en-US-u-ca-japanese")
        assertEquals("en", locale.language)
        assertEquals("US", locale.region)
        assertEquals("en-US-u-ca-japanese", locale.toLanguageTag())
    }

    test("canonicalisesExtensionOrder") {
        val locale = Locale.forLanguageTag("en-u-foo-bar-nu-thai-ca-buddhist-kk-true")
        assertEquals("en-u-bar-foo-ca-buddhist-kk-nu-thai", locale.toLanguageTag())
    }

    test("ordersSingletonsAlphabeticallyWithPrivateUseLast") {
        val locale = Locale.forLanguageTag("en-u-hc-h23-t-de-x-lorem")
        assertEquals("en-t-de-u-hc-h23-x-lorem", locale.toLanguageTag())
    }

    test("privateUseKeepsEverySubtag") {
        assertEquals("en-x-a-b", Locale.forLanguageTag("en-x-a-b").toLanguageTag())
    }

    test("anEmptyExtensionIsDropped") {
        assertEquals("en", Locale.forLanguageTag("en-u").toLanguageTag())
        assertEquals("en", Locale.forLanguageTag("en-t").toLanguageTag())
    }

    test("aKeywordValueCanSpanSeveralSubtags") {
        val locale = Locale.forLanguageTag("en-u-ca-islamic-civil")
        assertEquals("en-u-ca-islamic-civil", locale.toLanguageTag())
    }

    test("aKeywordWithNoValueReadsAsTrue") {
        assertEquals("en-u-kk", Locale.forLanguageTag("en-u-kk-true").toLanguageTag())
        assertEquals("en-u-kk", Locale.forLanguageTag("en-u-kk").toLanguageTag())
    }

    test("readsOldKeywordSyntax") {
        assertEquals("en-US-u-hc-h23", Locale.forLanguageTag("en_US@hc=h23").toLanguageTag())
    }

    test("mapsOldKeywordAliases") {
        assertEquals("en-US-u-hc-h23", Locale.forLanguageTag("en_US@hours=h23").toLanguageTag())
        assertEquals(
            "en-u-ca-japanese-co-phonebk",
            Locale.forLanguageTag("en@collation=phonebk;calendar=japanese").toLanguageTag(),
        )
    }

    test("ignoresOldKeywordsWithoutAValue") {
        assertEquals("pt-BR", Locale.forLanguageTag("PT_br.UTF-8@latin").toLanguageTag())
        assertEquals("en-u-hc-h23", Locale.forLanguageTag("en@latin;hc=h23").toLanguageTag())
        assertEquals("en-u-hc-h23", Locale.forLanguageTag("en@hc=;hc=h23").toLanguageTag())
        assertEquals("en", Locale.forLanguageTag("en@hc=").toLanguageTag())
    }

    test("keywordsFromBothSyntaxesMerge") {
        assertEquals("de-DE-u-hc-h12-nu-latn", Locale.forLanguageTag("de-DE-u-nu-latn@hc=h12").toLanguageTag())
    }

    test("theOldSyntaxWinsOverTheSameKeyInTheNewOne") {
        assertEquals("en-u-hc-h23", Locale.forLanguageTag("en-u-hc-h11@hc=h23").toLanguageTag())
    }

    test("aCharsetSuffixAndKeywordsCoexist") {
        assertEquals("de-DE-u-hc-h23", Locale.forLanguageTag("de_DE.UTF-8@hc=h23").toLanguageTag())
    }

    test("extensionsAreLowercased") {
        assertEquals("en-US-u-hc-h23", Locale.forLanguageTag("EN-us-U-HC-H23").toLanguageTag())
    }

    test("extensionsParticipateInEquality") {
        assertTrue(Locale.forLanguageTag("en-u-hc-h23") != Locale.forLanguageTag("en"))
        assertEquals(Locale.forLanguageTag("en-u-hc-h23"), Locale.forLanguageTag("en-U-hc-h23"))
    }

    test("mapsLegacyLanguageCodes") {
        assertEquals("id-ID", Locale.forLanguageTag("in-ID").toLanguageTag())
        assertEquals("he", Locale.forLanguageTag("iw").toLanguageTag())
    }

    test("rejectsInvalidTags") {
        assertNull(Locale.forLanguageTagOrNull(""))
        assertNull(Locale.forLanguageTagOrNull("C"))
        assertNull(Locale.forLanguageTagOrNull("POSIX"))
        assertNull(Locale.forLanguageTagOrNull("123"))
    }

    test("normalizesSubtagCase") {
        val locale = Locale.of("EN", "latn", "gb")
        assertEquals("en-Latn-GB", locale.toLanguageTag())
    }

    test("localesAreValueObjects") {
        assertEquals(Locale.forLanguageTag("en-GB"), Locale.of("en", region = "GB"))
        assertTrue(Locale.forLanguageTag("en") != Locale.forLanguageTag("en-GB"))
    }

    test("localeRefsNameALocale") {
        val ref = object : LocaleRef {
            override val tag = "pt-BR"
        }
        assertEquals(Locale.of("pt", region = "BR"), ref.toLocale())
    }

    test("currentLocaleIsAlwaysUsable") {
        // Whatever the platform reports must parse into a usable locale.
        val current = Locale.current
        assertTrue(current.language.length in 2..8, "language was '${current.language}'")
    }

    test("readsAndWritesUnicodeKeywords") {
        val locale = Locale.forLanguageTag("de-DE-u-hc-h12")
        assertEquals("h12", locale.unicodeKeywordOrNull("hc"))
        assertNull(locale.unicodeKeywordOrNull("nu"))
        assertEquals("de-DE-u-hc-h23", locale.withUnicodeKeyword("hc", "h23").toLanguageTag())
        assertEquals("de-DE", locale.withUnicodeKeyword("hc", null).toLanguageTag())
        assertEquals(Locale.forLanguageTag("de-DE-u-hc-h23"), Locale.forLanguageTag("de-DE").withUnicodeKeyword("HC", "H23"))
    }

    test("readsUnicodeAttributes") {
        assertEquals(setOf("bar", "foo"), Locale.forLanguageTag("en-u-foo-bar-hc-h23").unicodeAttributes)
    }

    test("stripExtensionsLeavesTheLanguageIdentifier") {
        val locale = Locale.forLanguageTag("sr-Cyrl-BA-u-hc-h23-x-lorem")
        assertEquals(Locale.forLanguageTag("sr-Cyrl-BA"), locale.stripExtensions())
    }

    test("extensionsNeverReachDataLookup") {
        val plain = Locale.forLanguageTag("pt-BR")
        val extended = Locale.forLanguageTag("pt-BR-u-foo-hc-h12-nu-latn")
        assertEquals(plain.dataLookupTags(), extended.dataLookupTags())
    }

    test("aKeywordKeyMustBeTwoCharacters") {
        val locale = Locale.forLanguageTag("de-DE")
        assertFailsWith<IllegalArgumentException> { locale.withUnicodeKeyword("hour", "h23") }
        assertFailsWith<IllegalArgumentException> { locale.withUnicodeKeyword("h", "h23") }
    }

    test("aKeywordValueMustBeWellFormed") {
        val locale = Locale.forLanguageTag("de-DE")
        assertFailsWith<IllegalArgumentException> { locale.withUnicodeKeyword("hc", "x") }
        assertFailsWith<IllegalArgumentException> { locale.withUnicodeKeyword("hc", "") }
    }

    test("aMalformedOldSyntaxValueIsDropped") {
        assertEquals("en", Locale.forLanguageTag("en@hc=x").toLanguageTag())
    }

    test("everythingWithUnicodeKeywordWritesRoundTrips") {
        val written = Locale.forLanguageTag("de-DE").withUnicodeKeyword("hc", "h23")
        assertEquals(written, Locale.forLanguageTag(written.toLanguageTag()))
    }

    test("theCurrentLocaleCarriesWhateverCycleThePlatformNames") {
        val keyword = Locale.current.unicodeKeywordOrNull("hc")
        assertTrue(keyword == null || keyword in setOf("h11", "h12", "h23", "h24", "c12", "c24"), "hc was '$keyword'")
    }

    test("theCurrentLocaleTagRoundTripsThePlatformCycle") {
        val current = Locale.current
        assertEquals(current, Locale.forLanguageTag(current.toLanguageTag()))
        platformHourCycleKeyword()?.let { assertEquals(it, current.unicodeKeywordOrNull("hc")) }
    }
}
