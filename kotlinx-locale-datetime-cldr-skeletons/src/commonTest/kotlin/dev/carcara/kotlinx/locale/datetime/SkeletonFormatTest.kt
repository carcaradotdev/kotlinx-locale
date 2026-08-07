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
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.CldrDateTimeSkeletons
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.format
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.skeletonPatternOrNull
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

private val date = LocalDate(2026, 7, 27)
private val time = LocalTime(15, 5, 9)

private fun locale(tag: String) = Locale.forLanguageTag(tag)

val SkeletonFormatTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("the same fields are arranged differently per locale") {
        assertEquals("27 de jul. de 2026", date.format("yMMMd", locale("pt-BR")))
        assertEquals("2026年7月27日", date.format("yMMMd", locale("ja")))
        assertEquals("Jul 27, 2026", date.format("yMMMd", locale("en")))
        assertEquals("27. Juli 2026", date.format("yMMMMd", locale("de")))
    }

    test("a skeleton is order independent") {
        assertEquals(
            date.format("yMMMd", locale("en")),
            date.format("dMMMy", locale("en")),
        )
    }

    test("weekday and month without a year") {
        assertEquals("Mon, Jul 27", date.format("MMMEd", locale("en")))
        assertEquals("seg., 27 de jul.", date.format("MMMEd", locale("pt-BR")))
    }

    test("width comes from the request") {
        assertEquals("MMM d, y", skeletonPatternOrNull("yMMMd", locale("en")))
        assertEquals("MMMM d, y", skeletonPatternOrNull("yMMMMd", locale("en")))
        assertEquals("M/d/y", skeletonPatternOrNull("yMd", locale("en")))
    }

    test("hour and minute widths stay the locale's") {
        // The hour keeps the width the locale wrote, so hhmm does not widen
        // en's h:mm. CLDR separates the time from the day period with U+202F.
        assertEquals("h:mm\u202Fa", skeletonPatternOrNull("hhmm", locale("en")))
        assertEquals("HH:mm", skeletonPatternOrNull("Hmm", locale("en")))
    }

    test("j resolves to the locale's preferred hour") {
        assertEquals("h:mm\u202Fa", skeletonPatternOrNull("jm", locale("en")))
        assertEquals("HH:mm", skeletonPatternOrNull("jm", locale("en-GB")))
        assertEquals("HH:mm", skeletonPatternOrNull("jm", locale("pt-BR")))
        assertEquals("3:05\u202FPM", time.format("jm", locale("en")))
        assertEquals("15:05", time.format("jm", locale("en-GB")))
    }

    test("capital J asks for an hour with no day period") {
        // The day period goes, but the width stays the one en wrote for a
        // 24-hour pattern, which is HH — so the twelve-hour form is hh.
        assertEquals("hh:mm", skeletonPatternOrNull("Jm", locale("en")))
        assertEquals("HH:mm", skeletonPatternOrNull("Jm", locale("en-GB")))
    }

    test("quarters render from the skeleton tables") {
        assertEquals("Q3 2026", date.format("yQQQ", locale("en")))
        assertEquals("3rd quarter 2026", date.format("yQQQQ", locale("en")))
        assertEquals("T3 de 2026", date.format("yQQQ", locale("pt-BR")))
    }

    test("a date and time request is joined with the locale's glue") {
        val dateTime = LocalDateTime(date, time)
        assertEquals("Jul 27, 2026, 3:05\u202FPM", dateTime.format("yMMMdjm", locale("en")))
        assertEquals("27 de jul. de 2026, 15:05", dateTime.format("yMMMdjm", locale("pt-BR")))
    }

    test("an unrenderable field is refused rather than dropped") {
        // Week numbering, zones and fractional seconds are all out of scope, and
        // answering them with a pattern one field short would be worse than not
        // answering.
        assertNull(skeletonPatternOrNull("yw", locale("en")))
        assertNull(skeletonPatternOrNull("Hmsv", locale("en")))
        assertNull(skeletonPatternOrNull("hmsSS", locale("en")))
    }

    test("every locale answers the ids CLDR declares everywhere") {
        for (locale in CldrDateTimeSkeletons.supportedLocales) {
            for (skeleton in listOf("yMMMd", "yMd", "MMMEd", "jm", "Hms", "yQQQ", "Ed", "yMMMM")) {
                val pattern = CldrDateTimeSkeletons.skeletonPatternOrNull(skeleton, locale)
                checkNotNull(pattern) { "no pattern for '$skeleton' in $locale" }
                check(pattern.isNotEmpty()) { "empty pattern for '$skeleton' in $locale" }
            }
        }
    }
}
