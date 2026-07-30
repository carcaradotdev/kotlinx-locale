package dev.carcara.kotlinx.locale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocaleTest {

    @Test
    fun parsesBcp47Tags() {
        val locale = Locale.forLanguageTag("pt-BR")
        assertEquals("pt", locale.language)
        assertEquals("BR", locale.region)
        assertNull(locale.script)
        assertEquals("pt-BR", locale.toLanguageTag())
    }

    @Test
    fun parsesPosixIdentifiers() {
        val locale = Locale.forLanguageTag("PT_br.UTF-8@latin")
        assertEquals("pt", locale.language)
        assertEquals("BR", locale.region)
        assertEquals("pt-BR", locale.toLanguageTag())
    }

    @Test
    fun parsesScriptAndRegion() {
        val locale = Locale.forLanguageTag("sr-Cyrl-BA")
        assertEquals("sr", locale.language)
        assertEquals("Cyrl", locale.script)
        assertEquals("BA", locale.region)
        assertEquals("sr-Cyrl-BA", locale.toLanguageTag())
    }

    @Test
    fun parsesVariants() {
        val locale = Locale.forLanguageTag("ca-ES-VALENCIA")
        assertEquals("valencia", locale.variant)
        assertEquals("ca-ES-valencia", locale.toLanguageTag())
    }

    @Test
    fun dropsExtensions() {
        val locale = Locale.forLanguageTag("en-US-u-ca-japanese")
        assertEquals("en-US", locale.toLanguageTag())
    }

    @Test
    fun mapsLegacyLanguageCodes() {
        assertEquals("id-ID", Locale.forLanguageTag("in-ID").toLanguageTag())
        assertEquals("he", Locale.forLanguageTag("iw").toLanguageTag())
    }

    @Test
    fun rejectsInvalidTags() {
        assertNull(Locale.forLanguageTagOrNull(""))
        assertNull(Locale.forLanguageTagOrNull("C"))
        assertNull(Locale.forLanguageTagOrNull("POSIX"))
        assertNull(Locale.forLanguageTagOrNull("123"))
    }

    @Test
    fun normalizesSubtagCase() {
        val locale = Locale.of("EN", "latn", "gb")
        assertEquals("en-Latn-GB", locale.toLanguageTag())
    }

    @Test
    fun localesAreValueObjects() {
        assertEquals(Locale.forLanguageTag("en-GB"), Locale.of("en", region = "GB"))
        assertTrue(Locale.forLanguageTag("en") != Locale.forLanguageTag("en-GB"))
    }

    @Test
    fun localeRefsNameALocale() {
        val ref = object : LocaleRef {
            override val tag = "pt-BR"
        }
        assertEquals(Locale.of("pt", region = "BR"), ref.toLocale())
    }

    @Test
    fun currentLocaleIsAlwaysUsable() {
        // Whatever the platform reports must parse into a usable locale.
        val current = Locale.current
        assertTrue(current.language.length in 2..8, "language was '${current.language}'")
    }
}
