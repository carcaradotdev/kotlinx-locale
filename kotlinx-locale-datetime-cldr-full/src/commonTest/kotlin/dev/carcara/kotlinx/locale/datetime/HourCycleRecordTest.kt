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

@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.datetime

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.internal.data.localeDataRegistry
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.DateTimeRecord
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.resolvedRecord
import dev.carcara.kotlinx.locale.test.assertEquals

private fun recordFor(tag: String) = localeDataFor(Locale.forLanguageTag(tag))

val HourCycleRecordTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("c12ResolvesAgainstTheAllowedList") {
        assertEquals(HourCycle.H11, recordFor("ja").resolveHourCycle(HourCycle.C12))
        assertEquals(HourCycle.H12, recordFor("en").resolveHourCycle(HourCycle.C12))
        assertEquals(HourCycle.H23, recordFor("ja").resolveHourCycle(HourCycle.C24))
        assertEquals(HourCycle.H23, recordFor("en").resolveHourCycle(HourCycle.C24))
        assertEquals(listOf("hB", "h", "H"), recordFor("hi-IN").hourAllowed)
        assertEquals(HourCycle.H12, recordFor("hi-IN").resolveHourCycle(HourCycle.C12))
        assertEquals(HourCycle.H23, recordFor("hi-IN").resolveHourCycle(HourCycle.C24))
    }

    test("aConcreteCycleResolvesToItself") {
        assertEquals(HourCycle.H24, recordFor("en").resolveHourCycle(HourCycle.H24))
    }

    test("crossingFamiliesTakesTheAlternatePattern") {
        assertEquals("HH:mm", recordFor("en").timePattern(FormatStyle.SHORT, HourCycle.H23))
    }

    test("stayingInTheFamilySwapsOneLetter") {
        assertEquals("K:mm\u202Fa", recordFor("en").timePattern(FormatStyle.SHORT, HourCycle.H11))
        assertEquals("kk:mm", recordFor("de").timePattern(FormatStyle.SHORT, HourCycle.H24))
    }

    test("noCycleLeavesThePatternAlone") {
        assertEquals(recordFor("en").timeFormats[3], recordFor("en").timePattern(FormatStyle.SHORT, null))
    }

    test("anEmptyAlternateFallsBackToTheOwnPattern") {
        val record = recordFor("byn")
        assertEquals(record.timeFormats[0], record.timePattern(FormatStyle.FULL, HourCycle.H23))
    }

    test("aRecordWithNoHourDataStillAnswers") {
        val full = requireNotNull(resolvedRecord(localeDataRegistry, Locale.forLanguageTag("en")))
        val truncated = full.split(FIELD_SEPARATOR).take(26).joinToString(FIELD_SEPARATOR.toString())
        val record = DateTimeRecord(truncated)
        assertEquals(record.timeFormats[3], record.timePattern(FormatStyle.SHORT, HourCycle.H23))
        assertEquals(HourCycle.H12, record.resolveHourCycle(HourCycle.C12))
    }

    test("theLetterSwapLeavesQuotedTextAlone") {
        val record = recordFor("fr-CA")
        assertEquals("kk 'h' mm", record.timePattern(FormatStyle.SHORT, HourCycle.H24))
    }
}
