package dev.carcara.kotlinx.locale

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNull
import dev.carcara.kotlinx.locale.test.assertTrue

val LocaleTest by matrixSuite {

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

    test("dropsExtensions") {
        val locale = Locale.forLanguageTag("en-US-u-ca-japanese")
        assertEquals("en-US", locale.toLanguageTag())
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
}
