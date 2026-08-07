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
