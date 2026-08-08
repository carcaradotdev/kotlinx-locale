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
import dev.carcara.kotlinx.locale.datetime.cldr.CldrDateTime
import dev.carcara.kotlinx.locale.datetime.cldr.displayName
import dev.carcara.kotlinx.locale.datetime.cldr.format
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.formatPattern
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.parseDateTimePattern
import dev.carcara.kotlinx.locale.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * End-to-end sweep: every bundled locale must format every style without
 * throwing and without producing blank output.
 */
val AllLocalesSmokeTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val date = LocalDate(2026, 7, 27)
    val time = LocalTime(15, 5, 9)
    val dateTime = LocalDateTime(date, time)

    test("everyLocaleFormatsEveryStyle") {
        for (locale in CldrDateTime.supportedLocales) {
            for (style in FormatStyle.entries) {
                val dateResult = date.format(style, locale)
                assertTrue(dateResult.isNotBlank(), "$locale date $style was blank")
                val timeResult = time.format(style, locale)
                assertTrue(timeResult.isNotBlank(), "$locale time $style was blank")
                val dateTimeResult = dateTime.format(style, locale)
                assertTrue(
                    dateResult in dateTimeResult && timeResult in dateTimeResult,
                    "$locale dateTime $style '$dateTimeResult' does not contain " +
                        "'$dateResult' and '$timeResult'",
                )
            }
        }
    }

    test("everyLocaleResolvesADayPeriodAtEveryHour") {
        val bTokens = parseDateTimePattern("B")
        val bLowerTokens = parseDateTimePattern("b")
        for (locale in CldrDateTime.supportedLocales) {
            val data = localeDataFor(locale)
            for (hour in 0..23) {
                for (probe in listOf(LocalTime(hour, 0), LocalTime(hour, 30, 9))) {
                    val flexible = formatPattern(bTokens, data, date = null, time = probe)
                    assertTrue(flexible.isNotBlank(), "$locale B at $probe was blank")
                    val amPm = formatPattern(bLowerTokens, data, date = null, time = probe)
                    assertTrue(amPm.isNotBlank(), "$locale b at $probe was blank")
                }
            }
        }
    }

    test("everyLocaleHasNamesForEveryMonthAndDay") {
        for (locale in CldrDateTime.supportedLocales) {
            for (style in TextStyle.entries) {
                for (month in Month.entries) {
                    assertTrue(
                        month.displayName(style, locale).isNotBlank(),
                        "$locale $month $style was blank",
                    )
                }
                for (day in DayOfWeek.entries) {
                    assertTrue(
                        day.displayName(style, locale).isNotBlank(),
                        "$locale $day $style was blank",
                    )
                }
            }
        }
    }
}
