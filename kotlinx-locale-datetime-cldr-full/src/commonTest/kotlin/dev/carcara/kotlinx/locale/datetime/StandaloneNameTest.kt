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

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.CldrDateTime
import dev.carcara.kotlinx.locale.datetime.cldr.displayName
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val CS = Locale.of("cs")
private val HR = Locale.of("hr")
private val EN = Locale.of("en")

/**
 * The format names go inside a date; the stand-alone names stand on their own.
 * In several languages the two differ by grammatical case, and putting the wrong
 * one on a calendar header is the kind of mistake a reader notices and a test
 * does not, unless it is this one.
 */
class StandaloneNameTest {

    @Test
    fun czechMonthsCarryTheirCase() {
        assertEquals("července", Month.JULY.displayName(TextStyle.FULL, CS))
        assertEquals("července", Month.JULY.displayName(TextStyle.FULL, NameContext.FORMAT, CS))
        assertEquals("červenec", Month.JULY.displayName(TextStyle.FULL, NameContext.STANDALONE, CS))
        assertEquals("ledna", Month.JANUARY.displayName(TextStyle.FULL, CS))
        assertEquals("leden", Month.JANUARY.displayName(TextStyle.FULL, NameContext.STANDALONE, CS))
    }

    @Test
    fun croatianDiffersInWidthAsWellAsCase() {
        assertEquals("srpnja", Month.JULY.displayName(TextStyle.FULL, HR))
        assertEquals("srpanj", Month.JULY.displayName(TextStyle.FULL, NameContext.STANDALONE, HR))
        // Stand-alone narrow is a number in Croatian, so this is not only a case
        // change and cannot be derived from the format form.
        assertEquals("7.", Month.JULY.displayName(TextStyle.NARROW, NameContext.STANDALONE, HR))
    }

    @Test
    fun englishAnswersTheSameInBothContexts() {
        for (month in Month.entries) {
            assertEquals(
                month.displayName(TextStyle.FULL, EN),
                month.displayName(TextStyle.FULL, NameContext.STANDALONE, EN),
            )
        }
        for (day in DayOfWeek.entries) {
            assertEquals(
                day.displayName(TextStyle.FULL, EN),
                day.displayName(TextStyle.FULL, NameContext.STANDALONE, EN),
            )
        }
    }

    @Test
    fun everyLocaleAnswersInBothContexts() {
        var differing = 0
        for (locale in CldrDateTime.supportedLocales) {
            for (month in Month.entries) {
                val format = month.displayName(TextStyle.FULL, NameContext.FORMAT, locale)
                val standalone = month.displayName(TextStyle.FULL, NameContext.STANDALONE, locale)
                assertTrue(format.isNotBlank() && standalone.isNotBlank(), "$locale ${month.name}")
                if (format != standalone) differing++
            }
        }
        assertTrue(differing > 500, "expected the languages that inflect months to differ, got $differing")
    }

    @Test
    fun theStandaloneWeekdayIsThereToo() {
        // Russian weekday names are the same in both contexts, but its months
        // are not, which is the pair worth pinning against a regression that
        // wired the two tables to the same field.
        val ru = Locale.of("ru")
        assertNotEquals(
            Month.JULY.displayName(TextStyle.FULL, NameContext.FORMAT, ru),
            Month.JULY.displayName(TextStyle.FULL, NameContext.STANDALONE, ru),
        )
        assertTrue(DayOfWeek.MONDAY.displayName(TextStyle.FULL, NameContext.STANDALONE, ru).isNotBlank())
    }
}
