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

package dev.carcara.kotlinx.locale.datetime.cldr

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.conformance.icuWeekDataGolden
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekInfoTest {

    @Test
    fun everyLocaleAgreesWithIcu() {
        val mismatches = ArrayList<String>()
        for ((tag, expected) in icuWeekDataGolden) {
            val (firstDay, minimalDays, weekendMask) = expected
            val actual = weekInfo(Locale.forLanguageTag(tag))
            val actualMask = actual.weekend.fold(0) { acc, day -> acc or (1 shl (day.isoDayNumber - 1)) }

            if (actual.firstDayOfWeek.isoDayNumber != firstDay ||
                actual.minimalDaysInFirstWeek != minimalDays ||
                actualMask != weekendMask
            ) {
                mismatches += "$tag: expected ($firstDay, $minimalDays, $weekendMask), " +
                    "got (${actual.firstDayOfWeek.isoDayNumber}, ${actual.minimalDaysInFirstWeek}, $actualMask)"
            }
        }
        assertTrue(mismatches.isEmpty(), "${mismatches.size} locales disagree with ICU:\n" + mismatches.take(20).joinToString("\n"))
    }

    @Test
    fun theGoldenCoversTheLocalesWorthCovering() {
        assertTrue(icuWeekDataGolden.size > 500, "the golden shrank to ${icuWeekDataGolden.size} locales")
    }

    @Test
    fun aLocaleWithNoRegionResolvesThroughLikelySubtags() {
        // The overlay is the only thing that can answer these, and getting it
        // wrong is invisible: every answer still looks like a plausible week.
        assertEquals(DayOfWeek.SUNDAY, weekInfo(Locale.forLanguageTag("en")).firstDayOfWeek)
        assertEquals(DayOfWeek.MONDAY, weekInfo(Locale.forLanguageTag("de")).firstDayOfWeek)
        assertEquals(4, weekInfo(Locale.forLanguageTag("de")).minimalDaysInFirstWeek)
    }

    @Test
    fun theRegionSubtagWinsOverTheLanguage() {
        // Same language, different answers. `en` alone maximises to the United
        // States, so a source that ignored the region would answer Sunday twice.
        assertEquals(DayOfWeek.SUNDAY, weekInfo(Locale.forLanguageTag("en-US")).firstDayOfWeek)
        assertEquals(DayOfWeek.MONDAY, weekInfo(Locale.forLanguageTag("en-GB")).firstDayOfWeek)
    }

    @Test
    fun britainKeepsItsMondayDespiteTheVariantRow() {
        // supplementalData.xml declares a Sunday first day for GB under
        // alt="variant", after the row that puts GB among the Monday territories.
        val gb = weekInfoForRegion("GB")
        assertEquals(DayOfWeek.MONDAY, gb.firstDayOfWeek)
        assertEquals(4, gb.minimalDaysInFirstWeek)
    }

    @Test
    fun theTwoFieldsVaryIndependently() {
        // Portugal starts on Sunday like the United States and wants four days in
        // the year like the rest of Europe, so neither field implies the other.
        val pt = weekInfoForRegion("PT")
        assertEquals(DayOfWeek.SUNDAY, pt.firstDayOfWeek)
        assertEquals(4, pt.minimalDaysInFirstWeek)
        assertEquals(1, weekInfoForRegion("US").minimalDaysInFirstWeek)
    }

    @Test
    fun aWeekendNeedNotBeSaturdayAndSunday() {
        assertEquals(setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), weekInfoForRegion("AF").weekend)
        assertEquals(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), weekInfoForRegion("IL").weekend)
        assertEquals(setOf(DayOfWeek.FRIDAY), weekInfoForRegion("IR").weekend)
        assertEquals(setOf(DayOfWeek.SUNDAY), weekInfoForRegion("IN").weekend)
    }

    @Test
    fun anUnknownRegionFallsBackToTheWorldDefault() {
        val unknown = weekInfoForRegion("ZZ")
        assertEquals(DayOfWeek.MONDAY, unknown.firstDayOfWeek)
        assertEquals(1, unknown.minimalDaysInFirstWeek)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), unknown.weekend)
    }

    @Test
    fun theRegionCodeIsCaseInsensitive() {
        assertEquals(weekInfoForRegion("PT").firstDayOfWeek, weekInfoForRegion("pt").firstDayOfWeek)
    }
}
