package dev.carcara.kotlinx.locale.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.DateTimeFormatSource
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.TextStyle
import dev.carcara.kotlinx.locale.datetime.displayName
import dev.carcara.kotlinx.locale.datetime.format
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SAMPLE_DATE = LocalDate(2026, 7, 27)
private val SAMPLE_TIME = LocalTime(15, 5, 9)

/**
 * Runs this source through the datetime conformance suite.
 *
 * Only the month and weekday names are comparable against ICU, because the
 * fixtures hold CLDR's *patterns* and the interface deliberately does not: no
 * platform can hand out a pattern, which is why the contract asks for formatted
 * output instead. The formatting checks are therefore behavioural at both
 * tiers, and the pattern tables are cross-checked inside the module that owns
 * them.
 */
public fun DateTimeFormatSource.assertConformsToDateTimeFormats(tier: ConformanceTier) {
    assertTrue(supportedLocales.isNotEmpty(), "a source that supports no locale answers nothing")

    if (tier == ConformanceTier.EXACT) assertMatchesIcuCalendarNames()
    assertEveryStyleRenders()
    assertNamesAreDistinctAndNonBlank()
}

private fun DateTimeFormatSource.assertMatchesIcuCalendarNames() {
    for (golden in icuGoldenData) {
        val locale = Locale.forLanguageTag(golden.tag)
        golden.monthsWide?.forEachIndexed { index, icuName ->
            assertEquals(
                icuName.normalizedSpaces(),
                displayName(Month(index + 1), TextStyle.FULL, locale).normalizedSpaces(),
                "${golden.tag} monthsWide[$index]",
            )
        }
        golden.monthsAbbr?.forEachIndexed { index, icuName ->
            assertEquals(
                icuName.normalizedSpaces(),
                displayName(Month(index + 1), TextStyle.ABBREVIATED, locale).normalizedSpaces(),
                "${golden.tag} monthsAbbr[$index]",
            )
        }
        golden.daysWide?.forEachIndexed { index, icuName ->
            assertEquals(
                icuName.normalizedSpaces(),
                displayName(DayOfWeek(index + 1), TextStyle.FULL, locale).normalizedSpaces(),
                "${golden.tag} daysWide[$index]",
            )
        }
        golden.daysAbbr?.forEachIndexed { index, icuName ->
            assertEquals(
                icuName.normalizedSpaces(),
                displayName(DayOfWeek(index + 1), TextStyle.ABBREVIATED, locale).normalizedSpaces(),
                "${golden.tag} daysAbbr[$index]",
            )
        }
    }
}

private fun DateTimeFormatSource.assertEveryStyleRenders() {
    val dateTime = LocalDateTime(SAMPLE_DATE, SAMPLE_TIME)
    for (tag in listOf("en", "de", "ja", "pt-BR", "ar-EG", "hi", "th", "he")) {
        val locale = Locale.forLanguageTag(tag)
        for (style in FormatStyle.entries) {
            assertTrue(format(SAMPLE_DATE, style, locale).isNotBlank(), "$tag date $style was blank")
            assertTrue(format(SAMPLE_TIME, style, locale).isNotBlank(), "$tag time $style was blank")
            assertTrue(format(dateTime, style, style, locale).isNotBlank(), "$tag date-time $style was blank")
        }
        // A date-time carries both halves, so it is never shorter than either.
        val date = format(SAMPLE_DATE, FormatStyle.MEDIUM, locale)
        val time = format(SAMPLE_TIME, FormatStyle.MEDIUM, locale)
        val both = format(dateTime, FormatStyle.MEDIUM, FormatStyle.MEDIUM, locale)
        assertTrue(both.length >= maxOf(date.length, time.length), "$tag glued output lost a half")
    }
}

private fun DateTimeFormatSource.assertNamesAreDistinctAndNonBlank() {
    for (tag in listOf("en", "de", "ja", "pt-BR", "ru")) {
        val locale = Locale.forLanguageTag(tag)
        val months = Month.entries.map { displayName(it, TextStyle.FULL, locale) }
        assertTrue(months.none(String::isBlank), "$tag has a blank month name")
        assertEquals(12, months.toSet().size, "$tag month names are not distinct")

        val days = DayOfWeek.entries.map { displayName(it, TextStyle.FULL, locale) }
        assertTrue(days.none(String::isBlank), "$tag has a blank weekday name")
        assertEquals(7, days.toSet().size, "$tag weekday names are not distinct")
    }
}
