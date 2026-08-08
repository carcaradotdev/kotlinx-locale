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
import dev.carcara.kotlinx.locale.datetime.cldr.relative.CldrRelativeTime
import dev.carcara.kotlinx.locale.datetime.cldr.relative.relativeTimeFormat
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue

private val EN = Locale.of("en")
private val CS = Locale.of("cs")

val CldrRelativeTimeTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("prefersTheWordWhereTheLocaleHasOne") {
        assertEquals("yesterday", relativeTimeFormat(-1L, RelativeTimeUnit.DAY, locale = EN))
        assertEquals("tomorrow", relativeTimeFormat(1L, RelativeTimeUnit.DAY, locale = EN))
        assertEquals("today", relativeTimeFormat(0L, RelativeTimeUnit.DAY, locale = EN))
        assertEquals("včera", relativeTimeFormat(-1L, RelativeTimeUnit.DAY, locale = CS))
        assertEquals("zítra", relativeTimeFormat(1L, RelativeTimeUnit.DAY, locale = CS))
        assertEquals("předevčírem", relativeTimeFormat(-2L, RelativeTimeUnit.DAY, locale = CS))
    }

    test("countsWhenAskedTo") {
        assertEquals("1 day ago", relativeTimeFormat(-1L, RelativeTimeUnit.DAY, numbering = RelativeTimeNumbering.ALWAYS, locale = EN))
        assertEquals("in 1 day", relativeTimeFormat(1L, RelativeTimeUnit.DAY, numbering = RelativeTimeNumbering.ALWAYS, locale = EN))
    }

    test("czechPicksAmongItsFourPluralForms") {
        // one, few, many and other are four different words, and this is what a
        // hand-rolled ladder that divides by seven gets wrong.
        assertEquals("před 1 dnem", relativeTimeFormat(-1L, RelativeTimeUnit.DAY, numbering = RelativeTimeNumbering.ALWAYS, locale = CS))
        assertEquals("před 3 dny", relativeTimeFormat(-3L, RelativeTimeUnit.DAY, locale = CS))
        assertEquals("před 10 dny", relativeTimeFormat(-10L, RelativeTimeUnit.DAY, locale = CS))
        assertEquals("za 3 dny", relativeTimeFormat(3L, RelativeTimeUnit.DAY, locale = CS))
        assertEquals("za 10 dní", relativeTimeFormat(10L, RelativeTimeUnit.DAY, locale = CS))
    }

    test("theWidthsFallBackToTheBase") {
        for (style in RelativeTimeStyle.entries) {
            assertTrue(
                relativeTimeFormat(-3L, RelativeTimeUnit.HOUR, style, locale = EN).isNotBlank(),
                "$style rendered nothing",
            )
        }
        assertEquals("3 hr. ago", relativeTimeFormat(-3L, RelativeTimeUnit.HOUR, RelativeTimeStyle.SHORT, locale = EN))
    }

    test("everyLocaleAndUnitAnswers") {
        var checked = 0
        for (locale in CldrRelativeTime.supportedLocales) {
            for (unit in RelativeTimeUnit.entries) {
                assertTrue(relativeTimeFormat(-3L, unit, locale = locale).isNotBlank(), "$locale $unit")
                checked++
            }
        }
        assertTrue(checked > 8000, "expected every locale and unit, got $checked")
    }
}
