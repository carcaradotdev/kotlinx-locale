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
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNull

val HourCycleTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("readsTheCycleOffTheLocale") {
        assertEquals(HourCycle.H23, Locale.forLanguageTag("de-DE-u-hc-h23").hourCycle)
        assertEquals(HourCycle.C12, Locale.forLanguageTag("ja-JP-u-hc-c12").hourCycle)
        assertNull(Locale.forLanguageTag("de-DE").hourCycle)
    }

    test("ignoresAnUnknownCycle") {
        assertNull(Locale.forLanguageTag("de-DE-u-hc-h99").hourCycle)
    }

    test("writesTheCycleOntoTheLocale") {
        val locale = Locale.forLanguageTag("de-DE")
        assertEquals("de-DE-u-hc-c24", locale.withHourCycle(HourCycle.C24).toLanguageTag())
        assertEquals("de-DE", locale.withHourCycle(HourCycle.C24).withHourCycle(null).toLanguageTag())
    }

    test("everyCycleRoundTrips") {
        for (cycle in HourCycle.entries) {
            assertEquals(cycle, Locale.forLanguageTag("en").withHourCycle(cycle).hourCycle)
        }
    }

    test("everyCycleNamesItsPatternLetter") {
        assertEquals('K', HourCycle.H11.patternLetter)
        assertEquals('h', HourCycle.H12.patternLetter)
        assertEquals('H', HourCycle.H23.patternLetter)
        assertEquals('k', HourCycle.H24.patternLetter)
        assertNull(HourCycle.C12.patternLetter)
        assertNull(HourCycle.C24.patternLetter)
    }

    test("clearingTheCycleKeepsTheOtherKeywords") {
        val locale = Locale.forLanguageTag("de-DE-u-ca-gregory-hc-h12")
        assertEquals("de-DE-u-ca-gregory", locale.withHourCycle(null).toLanguageTag())
        assertEquals("de-DE-u-ca-gregory-hc-h23", locale.withHourCycle(HourCycle.H23).toLanguageTag())
    }
}
