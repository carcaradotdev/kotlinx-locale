package dev.carcara.kotlinx.locale.datetime.cldr

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.DurationStyle
import dev.carcara.kotlinx.locale.test.assertEquals

val DurationPatternTest by matrixSuite {

    test("rootAnswersForAlmostEveryLocale") {
        for (tag in listOf("en", "de", "ja", "pt-BR", "ar", "hi", "zh-Hant", "ru")) {
            val locale = Locale.forLanguageTag(tag)
            assertEquals("h:mm", durationPattern(DurationStyle.HOUR_MINUTE, locale), tag)
            assertEquals("h:mm:ss", durationPattern(DurationStyle.HOUR_MINUTE_SECOND, locale), tag)
            assertEquals("m:ss", durationPattern(DurationStyle.MINUTE_SECOND, locale), tag)
        }
    }

    test("theNordicLocalesWriteAFullStop") {
        // The only two locales in CLDR 48.2 that override root, which is the
        // whole reason this is data rather than a constant.
        for (tag in listOf("fi", "da")) {
            val locale = Locale.forLanguageTag(tag)
            assertEquals("h.mm", durationPattern(DurationStyle.HOUR_MINUTE, locale), tag)
            assertEquals("h.mm.ss", durationPattern(DurationStyle.HOUR_MINUTE_SECOND, locale), tag)
            assertEquals("m.ss", durationPattern(DurationStyle.MINUTE_SECOND, locale), tag)
        }
    }

    test("aRegionalVariantInheritsItsLanguage") {
        assertEquals("m.ss", durationPattern(DurationStyle.MINUTE_SECOND, Locale.forLanguageTag("fi-FI")))
    }

    test("anUnknownLocaleFallsBackToRoot") {
        assertEquals("m:ss", durationPattern(DurationStyle.MINUTE_SECOND, Locale.forLanguageTag("zxx")))
    }
}
