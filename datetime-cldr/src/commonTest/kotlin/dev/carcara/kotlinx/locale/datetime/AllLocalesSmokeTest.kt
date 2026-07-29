@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.datetime.cldr.CldrDateTime
import dev.carcara.kotlinx.locale.datetime.cldr.displayName
import dev.carcara.kotlinx.locale.datetime.cldr.format
import dev.carcara.kotlinx.locale.datetime.cldr.format.formatPattern
import dev.carcara.kotlinx.locale.datetime.cldr.format.parseDateTimePattern
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end sweep: every bundled locale must format every style without
 * throwing and without producing blank output.
 */
class AllLocalesSmokeTest {

    private val date = LocalDate(2026, 7, 27)
    private val time = LocalTime(15, 5, 9)
    private val dateTime = LocalDateTime(date, time)

    @Test
    fun everyLocaleFormatsEveryStyle() {
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

    @Test
    fun everyLocaleResolvesADayPeriodAtEveryHour() {
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

    @Test
    fun everyLocaleHasNamesForEveryMonthAndDay() {
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
