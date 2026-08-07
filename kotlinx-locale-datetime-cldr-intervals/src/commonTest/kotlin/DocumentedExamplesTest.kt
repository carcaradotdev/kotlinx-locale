package dev.carcara.kotlinx.locale.datetime.cldr.intervals

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.DurationStyle
import dev.carcara.kotlinx.locale.datetime.cldr.durationPattern
import dev.carcara.kotlinx.locale.datetime.cldr.weekInfo
import dev.carcara.kotlinx.locale.datetime.cldr.weekInfoForRegion
import dev.carcara.kotlinx.locale.test.assertEquals
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** Every interval, week data and duration example in API.md. */
val DocumentedExamplesTest by matrixSuite {

    val en = Locale.forLanguageTag("en")

    fun spaces(text: String) = text.map { if (it.isWhitespace()) ' ' else it }.joinToString("")

    test("theIntervalExamples") {
        assertEquals("Jul 22, 2026", spaces(intervalFormat(LocalDate(2026, 7, 22), LocalDate(2026, 7, 22), "yMMMd", en)))
        assertEquals("Jul 18 – 22, 2026", spaces(intervalFormat(LocalDate(2026, 7, 18), LocalDate(2026, 7, 22), "yMMMd", en)))
        assertEquals("May 18 – Jul 22, 2026", spaces(intervalFormat(LocalDate(2026, 5, 18), LocalDate(2026, 7, 22), "yMMMd", en)))
        assertEquals("May 18, 2025 – Jul 22, 2026", spaces(intervalFormat(LocalDate(2025, 5, 18), LocalDate(2026, 7, 22), "yMMMd", en)))
    }

    test("theWeekDataExamples") {
        assertEquals(DayOfWeek.MONDAY, weekInfo(Locale.forLanguageTag("en-GB")).firstDayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, weekInfo(Locale.forLanguageTag("en-US")).firstDayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, weekInfoForRegion("PT").firstDayOfWeek)
        assertEquals(4, weekInfoForRegion("PT").minimalDaysInFirstWeek)
        assertEquals(setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), weekInfoForRegion("AF").weekend)
        assertEquals(setOf(DayOfWeek.FRIDAY), weekInfoForRegion("IR").weekend)
    }

    test("theDurationExamples") {
        assertEquals("m:ss", durationPattern(DurationStyle.MINUTE_SECOND, en))
        assertEquals("m.ss", durationPattern(DurationStyle.MINUTE_SECOND, Locale.forLanguageTag("fi")))
    }
}
