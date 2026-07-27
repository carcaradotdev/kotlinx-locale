package dev.srsouza.kotlinx.datetime.locale

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalizedFormatTest {

    private val date = LocalDate(2026, 7, 27) // a Monday
    private val time = LocalTime(15, 5, 9)
    private val dateTime = LocalDateTime(date, time)

    private fun locale(tag: String) = Locale.forLanguageTag(tag)

    @Test
    fun formatsDatesInEnglish() {
        assertEquals("Monday, July 27, 2026", date.format(FormatStyle.FULL, locale("en")))
        assertEquals("July 27, 2026", date.format(FormatStyle.LONG, locale("en")))
        assertEquals("Jul 27, 2026", date.format(FormatStyle.MEDIUM, locale("en")))
        assertEquals("7/27/26", date.format(FormatStyle.SHORT, locale("en")))
    }

    @Test
    fun formatsDatesInBritishEnglish() {
        assertEquals("Monday, 27 July 2026", date.format(FormatStyle.FULL, locale("en-GB")))
        assertEquals("27 July 2026", date.format(FormatStyle.LONG, locale("en-GB")))
        assertEquals("27/07/2026", date.format(FormatStyle.SHORT, locale("en-GB")))
    }

    @Test
    fun formatsDatesInGerman() {
        assertEquals("Montag, 27. Juli 2026", date.format(FormatStyle.FULL, locale("de")))
        assertEquals("27.07.2026", date.format(FormatStyle.MEDIUM, locale("de")))
    }

    @Test
    fun formatsDatesInPortuguese() {
        assertEquals(
            "segunda-feira, 27 de julho de 2026",
            date.format(FormatStyle.FULL, locale("pt-BR")),
        )
        assertEquals("27 de julho de 2026", date.format(FormatStyle.LONG, locale("pt-BR")))
    }

    @Test
    fun formatsDatesInFrench() {
        assertEquals("27 juillet 2026", date.format(FormatStyle.LONG, locale("fr")))
    }

    @Test
    fun formatsDatesInJapanese() {
        assertEquals("2026年7月27日月曜日", date.format(FormatStyle.FULL, locale("ja")))
        assertEquals("2026/07/27", date.format(FormatStyle.MEDIUM, locale("ja")))
    }

    @Test
    fun formatsWithNonLatinDigits() {
        // CLDR 48 defaults plain `ar` to Latin digits, but ar-EG keeps arab
        // digits and fa uses extended Arabic-Indic digits.
        val arabicEgypt = date.format(FormatStyle.LONG, locale("ar-EG"))
        assertTrue("٢٠٢٦" in arabicEgypt, "expected arab year digits in '$arabicEgypt'")
        assertTrue("٢٧" in arabicEgypt, "expected arab day digits in '$arabicEgypt'")
        val persian = date.format(FormatStyle.LONG, locale("fa"))
        assertTrue("۲۰۲۶" in persian, "expected arabext year digits in '$persian'")
    }

    // CLDR separates times from day-period markers with U+202F (narrow no-break space).
    private val nnbsp = ' '

    @Test
    fun formatsTimes() {
        assertEquals("3:05${nnbsp}PM", time.format(FormatStyle.SHORT, locale("en")))
        assertEquals("3:05:09${nnbsp}PM", time.format(FormatStyle.MEDIUM, locale("en")))
        assertEquals("15:05", time.format(FormatStyle.SHORT, locale("en-GB")))
        assertEquals("15:05:09", time.format(FormatStyle.MEDIUM, locale("de")))
    }

    @Test
    fun stripsZoneFieldsFromZonelessTimeStyles() {
        // The FULL time pattern for en is "h:mm:ss a zzzz"; LocalTime has no zone.
        assertEquals("3:05:09${nnbsp}PM", time.format(FormatStyle.FULL, locale("en")))
        assertEquals("15:05:09", time.format(FormatStyle.FULL, locale("en-GB")))
    }

    @Test
    fun formatsMorningTimes() {
        assertEquals("9:05${nnbsp}AM", LocalTime(9, 5).format(FormatStyle.SHORT, locale("en")))
        assertEquals("12:30${nnbsp}AM", LocalTime(0, 30).format(FormatStyle.SHORT, locale("en")))
        assertEquals("12:30${nnbsp}PM", LocalTime(12, 30).format(FormatStyle.SHORT, locale("en")))
    }

    @Test
    fun formatsDateTimesWithGluePattern() {
        assertEquals("7/27/26, 3:05${nnbsp}PM", dateTime.format(FormatStyle.SHORT, locale("en")))
        assertEquals(
            "Jul 27, 2026, 3:05:09${nnbsp}PM",
            dateTime.format(FormatStyle.MEDIUM, locale("en")),
        )
        assertEquals(
            "Monday, July 27, 2026, 3:05:09${nnbsp}PM",
            dateTime.format(FormatStyle.FULL, locale("en")),
        )
        assertEquals("27.07.2026, 15:05:09", dateTime.format(FormatStyle.MEDIUM, locale("de")))
    }

    @Test
    fun formatsMixedDateTimeStyles() {
        assertEquals(
            "July 27, 2026, 3:05${nnbsp}PM",
            dateTime.format(FormatStyle.LONG, FormatStyle.SHORT, locale("en")),
        )
    }

    @Test
    fun formatsMonthNames() {
        assertEquals("July", Month.JULY.displayName(TextStyle.FULL, locale("en")))
        assertEquals("Jul", Month.JULY.displayName(TextStyle.ABBREVIATED, locale("en")))
        assertEquals("J", Month.JULY.displayName(TextStyle.NARROW, locale("en")))
        assertEquals("julho", Month.JULY.displayName(TextStyle.FULL, locale("pt-BR")))
        assertEquals("juillet", Month.JULY.displayName(TextStyle.FULL, locale("fr")))
    }

    @Test
    fun formatsDayOfWeekNames() {
        assertEquals("Monday", DayOfWeek.MONDAY.displayName(TextStyle.FULL, locale("en")))
        assertEquals("Mon", DayOfWeek.MONDAY.displayName(TextStyle.ABBREVIATED, locale("en")))
        assertEquals("M", DayOfWeek.MONDAY.displayName(TextStyle.NARROW, locale("en")))
        assertEquals("Montag", DayOfWeek.MONDAY.displayName(TextStyle.FULL, locale("de")))
        assertEquals("segunda-feira", DayOfWeek.MONDAY.displayName(TextStyle.FULL, locale("pt-BR")))
    }

    @Test
    fun fallsBackThroughTheLocaleChain() {
        // No CLDR data for en-XX: falls back to en.
        assertEquals("Jul 27, 2026", date.format(FormatStyle.MEDIUM, locale("en-XX")))
        // Unknown language: falls back to root (ISO-like patterns).
        assertEquals("2026-07-27", date.format(FormatStyle.SHORT, Locale.of("zz")))
    }
}
