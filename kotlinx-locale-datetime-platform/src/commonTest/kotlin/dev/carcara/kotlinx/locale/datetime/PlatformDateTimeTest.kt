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

package dev.carcara.kotlinx.locale.datetime

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToDateTimeFormats
import dev.carcara.kotlinx.locale.datetime.cldr.CldrDateTime
import dev.carcara.kotlinx.locale.datetime.displayName
import dev.carcara.kotlinx.locale.datetime.format
import dev.carcara.kotlinx.locale.datetime.platform.PlatformDateTime
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNotNull
import dev.carcara.kotlinx.locale.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * The platform datetime source, and the timezone trap it exists to avoid.
 */
val PlatformDateTimeTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val composed = FallbackDateTimeFormats(primary = PlatformDateTime, fallback = CldrDateTime)

    val en = Locale.of("en")
    val date = LocalDate(2026, 7, 27)
    val time = LocalTime(15, 5, 9)

    test("theCompositionConformsBehaviourally") {
        composed.assertConformsToDateTimeFormats(ConformanceTier.BEHAVIOURAL)
    }

    test("theCompositionNamesTheMonthsAndWeekdaysIdenticallyEverywhere") {
        // Every host and CLDR agree on the English calendar names, so these are
        // exact assertions that hold on every target: the platform answers on
        // JVM, JS and Apple, the bundled source answers on the other four.
        assertEquals("July", composed.displayName(Month(7), TextStyle.FULL, en))
        assertEquals("Jan", composed.displayName(Month(1), TextStyle.ABBREVIATED, en))
        assertEquals("Monday", composed.displayName(DayOfWeek(1), TextStyle.FULL, en))
        // Sunday is ISO 7 and Foundation indexes weekdays from Sunday, so this is
        // the off-by-one that a careless mapping gets wrong.
        assertEquals("Sunday", composed.displayName(DayOfWeek(7), TextStyle.FULL, en))
    }

    test("theCompositionRendersEveryStyleEverywhere") {
        val moment = LocalDateTime(date, time)
        for (tag in listOf("en", "de", "ja", "pt-BR", "ar-EG")) {
            val locale = Locale.forLanguageTag(tag)
            for (style in FormatStyle.entries) {
                assertTrue(composed.format(date, style, locale).isNotBlank(), "$tag date $style was blank")
                assertTrue(composed.format(time, style, locale).isNotBlank(), "$tag time $style was blank")
                assertTrue(composed.format(moment, style, style, locale).isNotBlank(), "$tag moment $style was blank")
            }
        }
    }

    test("theSourceHonoursItsAvailabilityContract") {
        if (PlatformDateTime.isAvailable) {
            PlatformDateTime.assertConformsToDateTimeFormats(ConformanceTier.BEHAVIOURAL)
        } else {
            // Linux, Windows, Android Native and WASI, asserted rather than skipped
            // so that a target growing locale data is noticed.
            assertEquals(null, PlatformDateTime.formatDateOrNull(date, FormatStyle.LONG, en))
            assertEquals(null, PlatformDateTime.formatTimeOrNull(time, FormatStyle.SHORT, en))
            assertEquals(null, PlatformDateTime.formatDateTimeOrNull(LocalDateTime(date, time), FormatStyle.LONG, FormatStyle.SHORT, en))
            assertEquals(null, PlatformDateTime.monthNameOrNull(7, TextStyle.FULL, en))
            assertEquals(null, PlatformDateTime.dayOfWeekNameOrNull(1, TextStyle.FULL, en))
            assertTrue(PlatformDateTime.supportedLocales.isEmpty())
        }
    }

    test("theDayThatGoesInIsTheDayThatComesOut") {
        if (!PlatformDateTime.isAvailable) {
            // Covered by the availability contract test; nothing to shift.
            assertEquals(null, PlatformDateTime.formatDateOrNull(date, FormatStyle.LONG, en))
            return@test
        }
        // The trap this guards: a LocalDate has no zone, and the platform
        // formatters take an instant plus a zone. Formatted in the host's zone,
        // 2026-07-27 renders as the 26th or the 28th depending on where the
        // machine is, which is a bug that only some users ever see. Every actual
        // formats in UTC for exactly this reason.
        for (style in listOf(FormatStyle.LONG, FormatStyle.MEDIUM, FormatStyle.SHORT)) {
            val formatted = assertNotNull(PlatformDateTime.formatDateOrNull(date, style, en))
            assertTrue("27" in formatted, "$style lost or shifted the day: '$formatted'")
            val year = if (style == FormatStyle.SHORT) "26" else "2026"
            assertTrue(year in formatted, "$style lost the year: '$formatted'")
        }
    }

    test("theHourThatGoesInIsTheHourThatComesOut") {
        if (!PlatformDateTime.isAvailable) {
            assertEquals(null, PlatformDateTime.formatDateOrNull(date, FormatStyle.LONG, en))
            return@test
        }
        val formatted = assertNotNull(PlatformDateTime.formatTimeOrNull(time, FormatStyle.MEDIUM, en))
        assertTrue("05" in formatted || "5" in formatted, "lost the minutes: '$formatted'")
        // 15:05 in a 12-hour locale is 3:05, so either form is correct; what must
        // not happen is the hour drifting with the machine's zone.
        assertTrue("3" in formatted || "15" in formatted, "lost or shifted the hour: '$formatted'")
    }

    test("theFourLengthsDifferFromEachOther") {
        if (!PlatformDateTime.isAvailable) {
            assertEquals(null, PlatformDateTime.formatDateOrNull(date, FormatStyle.LONG, en))
            return@test
        }
        val rendered = FormatStyle.entries.map { assertNotNull(PlatformDateTime.formatDateOrNull(date, it, en)) }
        // FULL and LONG can coincide in some locales, but all four collapsing to
        // one string would mean the style is being ignored.
        assertTrue(rendered.toSet().size > 1, "every length rendered the same string: $rendered")
        assertTrue(rendered.first().length >= rendered.last().length, "FULL was shorter than SHORT: $rendered")
    }

    test("theNamesAreLocalizedAndNotJustEnglish") {
        if (!PlatformDateTime.isAvailable) {
            assertEquals(null, PlatformDateTime.monthNameOrNull(7, TextStyle.FULL, en))
            return@test
        }
        assertEquals("July", PlatformDateTime.monthNameOrNull(7, TextStyle.FULL, en))
        assertEquals("Monday", PlatformDateTime.dayOfWeekNameOrNull(1, TextStyle.FULL, en))

        val german = PlatformDateTime.monthNameOrNull(7, TextStyle.FULL, Locale.of("de"))
        assertEquals("Juli", german)

        // Sunday is ISO 7, and Foundation indexes weekdays from Sunday, so this is
        // the off-by-one that mapping would get wrong.
        assertEquals("Sunday", PlatformDateTime.dayOfWeekNameOrNull(7, TextStyle.FULL, en))
    }

    test("theGluedDateTimeCarriesBothHalves") {
        if (!PlatformDateTime.isAvailable) {
            assertEquals(null, PlatformDateTime.formatDateOrNull(date, FormatStyle.LONG, en))
            return@test
        }
        val moment = LocalDateTime(date, time)
        val glued = assertNotNull(
            PlatformDateTime.formatDateTimeOrNull(moment, FormatStyle.MEDIUM, FormatStyle.SHORT, en),
        )
        assertTrue("27" in glued, "the date half is missing: '$glued'")
        assertTrue("05" in glued || "5" in glued, "the time half is missing: '$glued'")
    }

    test("anOutOfRangeMonthOrWeekdayIsRefused") {
        assertEquals(null, PlatformDateTime.monthNameOrNull(0, TextStyle.FULL, en))
        assertEquals(null, PlatformDateTime.monthNameOrNull(13, TextStyle.FULL, en))
        assertEquals(null, PlatformDateTime.dayOfWeekNameOrNull(0, TextStyle.FULL, en))
        assertEquals(null, PlatformDateTime.dayOfWeekNameOrNull(8, TextStyle.FULL, en))
    }
}
