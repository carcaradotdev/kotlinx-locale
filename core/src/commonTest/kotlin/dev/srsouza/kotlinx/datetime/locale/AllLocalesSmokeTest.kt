package dev.srsouza.kotlinx.datetime.locale

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
        for (locale in Locale.availableLocales) {
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
    fun everyLocaleHasNamesForEveryMonthAndDay() {
        for (locale in Locale.availableLocales) {
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
