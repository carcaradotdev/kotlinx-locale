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
import dev.carcara.kotlinx.locale.datetime.cldr.intervals.conformance.icuIntervalGolden
import dev.carcara.kotlinx.locale.datetime.cldr.intervals.conformance.icuIntervalGoldenCases
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue
import kotlinx.datetime.LocalDate

val IntervalFormatTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val en = Locale.forLanguageTag("en")

    /**
     * CLDR and ICU disagree about which invisible space they write, and the
     * difference is not a formatting decision. Same fold the shared conformance
     * assertions apply.
     */
    fun String.spaces(): String = map { if (it.isWhitespace()) ' ' else it }.joinToString("")

    fun date(text: String): LocalDate {
        val (y, m, d) = text.split('-').map(String::toInt)
        return LocalDate(y, m, d)
    }

    test("theFourCasesCollapseWhatTheyShare") {
        assertEquals("Jul 22, 2026", intervalFormat(date("2026-07-22"), date("2026-07-22"), "yMMMd", en).spaces())
        assertEquals("Jul 18 – 22, 2026", intervalFormat(date("2026-07-18"), date("2026-07-22"), "yMMMd", en).spaces())
        assertEquals("May 18 – Jul 22, 2026", intervalFormat(date("2026-05-18"), date("2026-07-22"), "yMMMd", en).spaces())
        assertEquals("May 18, 2025 – Jul 22, 2026", intervalFormat(date("2025-05-18"), date("2026-07-22"), "yMMMd", en).spaces())
    }

    test("anUnrenderableSkeletonFallsBackToTheIso8601IntervalForm") {
        // Week-of-year is not a field this library renders, so the matcher
        // refuses the skeleton and there is no locale answer to give. ISO
        // 8601-1:2019 clause 3.2.6 writes an interval as <start>/<end>, so that
        // is what comes back, rather than an en dash borrowed from English for a
        // locale we just failed to find data for.
        assertEquals(
            "2026-07-18/2026-07-22",
            intervalFormat(date("2026-07-18"), date("2026-07-22"), "w", en),
        )
        assertEquals(
            "2026-07-18/2026-07-22",
            intervalFormat(date("2026-07-18"), date("2026-07-22"), "w", Locale.forLanguageTag("ja")),
        )
    }

    test("anIdenticalPairFormatsOnce") {
        // The case most likely to come out as the same text twice with a dash.
        val once = intervalFormat(date("2026-07-22"), date("2026-07-22"), "yMMMd", en)
        assertTrue('–' !in once && '-' !in once, "an identical pair should not be joined: $once")
    }

    test("everyLocaleAgreesWithIcu") {
        val mismatches = ArrayList<String>()
        var compared = 0
        for ((tag, expected) in icuIntervalGolden) {
            val locale = Locale.forLanguageTag(tag)
            for ((index, case) in icuIntervalGoldenCases.withIndex()) {
                val (skeleton, start, end) = case
                val actual = intervalFormat(date(start), date(end), skeleton, locale)
                compared++
                if (actual.spaces() != expected[index].spaces()) {
                    mismatches += "$tag $skeleton $start..$end: expected '${expected[index]}', got '$actual'"
                }
            }
        }
        assertTrue(compared > 10_000, "the golden shrank to $compared comparisons")
        assertTrue(
            mismatches.isEmpty(),
            "${mismatches.size} of $compared disagree with ICU across " +
                "${mismatches.map { it.substringBefore(' ') }.toSet().size} locales:\n" +
                mismatches.take(25).joinToString("\n"),
        )
    }
}
