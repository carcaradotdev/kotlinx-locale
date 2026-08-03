package dev.carcara.kotlinx.locale.datetime.cldr

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.DurationStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class DurationPatternTest {

    @Test
    fun rootAnswersForAlmostEveryLocale() {
        for (tag in listOf("en", "de", "ja", "pt-BR", "ar", "hi", "zh-Hant", "ru")) {
            val locale = Locale.forLanguageTag(tag)
            assertEquals("h:mm", durationPattern(DurationStyle.HOUR_MINUTE, locale), tag)
            assertEquals("h:mm:ss", durationPattern(DurationStyle.HOUR_MINUTE_SECOND, locale), tag)
            assertEquals("m:ss", durationPattern(DurationStyle.MINUTE_SECOND, locale), tag)
        }
    }

    @Test
    fun theNordicLocalesWriteAFullStop() {
        // The only two locales in CLDR 48.2 that override root, which is the
        // whole reason this is data rather than a constant.
        for (tag in listOf("fi", "da")) {
            val locale = Locale.forLanguageTag(tag)
            assertEquals("h.mm", durationPattern(DurationStyle.HOUR_MINUTE, locale), tag)
            assertEquals("h.mm.ss", durationPattern(DurationStyle.HOUR_MINUTE_SECOND, locale), tag)
            assertEquals("m.ss", durationPattern(DurationStyle.MINUTE_SECOND, locale), tag)
        }
    }

    @Test
    fun aRegionalVariantInheritsItsLanguage() {
        assertEquals("m.ss", durationPattern(DurationStyle.MINUTE_SECOND, Locale.forLanguageTag("fi-FI")))
    }

    @Test
    fun anUnknownLocaleFallsBackToRoot() {
        assertEquals("m:ss", durationPattern(DurationStyle.MINUTE_SECOND, Locale.forLanguageTag("zxx")))
    }
}
