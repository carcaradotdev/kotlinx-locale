package dev.carcara.kotlinx.locale.catalog

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleRef
import dev.carcara.kotlinx.locale.toLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocaleCatalogTest {

    @Test
    fun namesTheBareLanguageAndItsRegions() {
        assertEquals("pt", Pt.BASE.tag)
        assertEquals("pt-BR", Pt.BR.tag)
        assertEquals("en-GB", En.GB.tag)
    }

    @Test
    fun flattensScriptsAndVariantsIntoOneLevel() {
        assertEquals("zh-Hans-CN", Zh.HANS_CN.tag)
        assertEquals("sr-Cyrl-BA", Sr.CYRL_BA.tag)
        assertEquals("ca-ES-valencia", Ca.ES_VALENCIA.tag)
    }

    @Test
    fun namesMacroregionsAfterTheirEnglishRegionName() {
        // 001, 150 and 419 are not valid Kotlin identifiers, and backticking
        // them would produce JVM field names Java callers cannot reference.
        assertEquals("ar-001", Ar.WORLD.tag)
        assertEquals("en-150", En.EUROPE.tag)
        assertEquals("es-419", Es.LATIN_AMERICA.tag)
    }

    @Test
    fun everyTagParsesBackToTheLocaleItNames() {
        for (ref in Pt.entries + Zh.entries + Es.entries + Ca.entries) {
            assertEquals(ref.tag, ref.toLocale().toLanguageTag(), "${ref.tag} did not round trip")
        }
    }

    @Test
    fun entriesCoverTheLanguage() {
        assertTrue(Pt.entries.size > 10, "expected every Portuguese locale, got ${Pt.entries.size}")
        assertTrue(En.entries.size > 100, "expected every English locale, got ${En.entries.size}")
    }

    @Test
    fun refsAreUsableWhereverALocaleRefIsAsked() {
        fun tagOf(ref: LocaleRef) = ref.tag
        assertEquals("pt-BR", tagOf(Pt.BR))
        assertEquals(Locale.forLanguageTag("pt-BR"), Pt.BR.toLocale())
    }
}
