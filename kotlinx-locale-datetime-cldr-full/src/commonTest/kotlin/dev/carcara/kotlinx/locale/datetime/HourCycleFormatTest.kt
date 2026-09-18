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
import dev.carcara.kotlinx.locale.LocaleDefaults
import dev.carcara.kotlinx.locale.datetime.cldr.format
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNotEquals
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

val HourCycleFormatTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val time = LocalTime(15, 30)
    val dateTime = LocalDateTime(2026, 7, 27, 15, 30)

    fun locale(tag: String) = Locale.forLanguageTag(tag)

    // CLDR separates a 12-hour time from its day period with U+202F, not an ASCII space.
    val nnbsp = '\u202F'

    test("a24HourLocaleCanBeAskedFor12") {
        assertEquals("3:30${nnbsp}PM", time.format(FormatStyle.SHORT, locale("de-DE-u-hc-h12")))
    }

    test("a12HourLocaleCanBeAskedFor24") {
        assertEquals("15:30", time.format(FormatStyle.SHORT, locale("en-US-u-hc-h23")))
    }

    test("aDayPeriodLeadingLocaleKeepsItInFrontAcrossFamilies") {
        assertEquals("下午3:30", time.format(FormatStyle.SHORT, locale("zh-CN-u-hc-h12")))
    }

    test("noCycleChangesNothing") {
        assertEquals(
            time.format(FormatStyle.SHORT, locale("de-DE")),
            time.format(FormatStyle.SHORT, locale("de-DE-u-nu-latn")),
        )
    }

    test("theGlueStaysTheLocalesOwn") {
        val plainLocale = locale("de-DE")
        val overriddenLocale = locale("de-DE-u-hc-h12")
        val plain = dateTime.format(FormatStyle.MEDIUM, FormatStyle.SHORT, plainLocale)
        val overridden = dateTime.format(FormatStyle.MEDIUM, FormatStyle.SHORT, overriddenLocale)

        val plainTime = time.format(FormatStyle.SHORT, plainLocale)
        val overriddenTime = time.format(FormatStyle.SHORT, overriddenLocale)
        assertNotEquals(plainTime, overriddenTime)

        assertEquals(plain.replace(plainTime, ""), overridden.replace(overriddenTime, ""))
        assertEquals("27.07.2026, 15:30", plain)
        assertEquals("27.07.2026, 3:30${nnbsp}PM", overridden)
    }

    test("germanStaysGermanUnderAnEnglishStyleClock") {
        val locale = locale("de-DE-u-hc-h12")
        assertEquals(
            "27.07.2026, 3:30${nnbsp}PM",
            dateTime.format(FormatStyle.MEDIUM, FormatStyle.SHORT, locale),
        )
        assertEquals("27.07.2026", dateTime.date.format(FormatStyle.MEDIUM, locale))
    }

    test("anApplicationWideDefaultReachesAFormatCall") {
        val restore = LocaleDefaults.locale
        try {
            LocaleDefaults.locale = locale("de-DE-u-hc-h12")
            assertEquals("3:30${nnbsp}PM", time.format(FormatStyle.SHORT, Locale.current))
        } finally {
            LocaleDefaults.locale = restore
        }
    }
}
