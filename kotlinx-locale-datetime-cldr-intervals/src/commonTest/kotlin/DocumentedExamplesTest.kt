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

package dev.carcara.kotlinx.locale.datetime.cldr.intervals

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.DurationStyle
import dev.carcara.kotlinx.locale.datetime.cldr.durationPattern
import dev.carcara.kotlinx.locale.datetime.cldr.weekInfo
import dev.carcara.kotlinx.locale.datetime.cldr.weekInfoForRegion
import dev.carcara.kotlinx.locale.test.assertEquals
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** Every interval, week data and duration example in API.md. */
val DocumentedExamplesTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

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
