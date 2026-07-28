package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.internal.formatPattern
import dev.carcara.kotlinx.locale.datetime.internal.localeDataFor
import dev.carcara.kotlinx.locale.datetime.internal.parseDateTimePattern
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the day period pattern fields: `B` (flexible day periods selected by
 * the CLDR rules from dayPeriods.xml) and `b` (AM/PM plus exact noon and
 * midnight). Expected values are the format/abbreviated names from CLDR.
 */
class DayPeriodTest {

    private fun dayPeriod(tag: String, time: LocalTime, pattern: String = "B"): String = formatPattern(
        parseDateTimePattern(pattern),
        localeDataFor(Locale.forLanguageTag(tag)),
        date = null,
        time = time,
    )

    @Test
    fun flexibleDayPeriodsInEnglish() {
        assertEquals("midnight", dayPeriod("en", LocalTime(0, 0)))
        // English mornings start at 00:00 in CLDR; night only runs 21:00-24:00.
        assertEquals("in the morning", dayPeriod("en", LocalTime(0, 30)))
        assertEquals("in the morning", dayPeriod("en", LocalTime(9, 0)))
        assertEquals("noon", dayPeriod("en", LocalTime(12, 0)))
        assertEquals("in the afternoon", dayPeriod("en", LocalTime(15, 0)))
        assertEquals("in the evening", dayPeriod("en", LocalTime(19, 0)))
        assertEquals("at night", dayPeriod("en", LocalTime(22, 0)))
    }

    @Test
    fun noonAndMidnightRequireTheExactTime() {
        assertEquals("in the afternoon", dayPeriod("en", LocalTime(12, 0, 30)))
        assertEquals("in the afternoon", dayPeriod("en", LocalTime(12, 1)))
        assertEquals("in the morning", dayPeriod("en", LocalTime(0, 0, 30)))
    }

    @Test
    fun flexibleDayPeriodsInGerman() {
        assertEquals("Mitternacht", dayPeriod("de", LocalTime(0, 0)))
        assertEquals("nachts", dayPeriod("de", LocalTime(3, 0)))
        assertEquals("morgens", dayPeriod("de", LocalTime(5, 0)))
        assertEquals("vorm.", dayPeriod("de", LocalTime(10, 30)))
        assertEquals("mittags", dayPeriod("de", LocalTime(12, 30)))
        assertEquals("nachm.", dayPeriod("de", LocalTime(14, 0)))
        assertEquals("abends", dayPeriod("de", LocalTime(19, 0)))
    }

    @Test
    fun nightIntervalsWrapPastMidnight() {
        // Russian night1 runs 22:00-04:00, crossing midnight.
        assertEquals("ночи", dayPeriod("ru", LocalTime(23, 0)))
        assertEquals("ночи", dayPeriod("ru", LocalTime(2, 0)))
        assertEquals("ночи", dayPeriod("ru", LocalTime(3, 59)))
        assertEquals("утра", dayPeriod("ru", LocalTime(4, 0)))
        assertEquals("полд.", dayPeriod("ru", LocalTime(12, 0)))
    }

    @Test
    fun amPmNoonMidnightField() {
        assertEquals("midnight", dayPeriod("en", LocalTime(0, 0), pattern = "b"))
        assertEquals("noon", dayPeriod("en", LocalTime(12, 0), pattern = "b"))
        assertEquals("AM", dayPeriod("en", LocalTime(0, 30), pattern = "b"))
        assertEquals("PM", dayPeriod("en", LocalTime(12, 30), pattern = "b"))
        assertEquals("PM", dayPeriod("en", LocalTime(15, 0), pattern = "b"))
        // German has a name for midnight but none for noon, so noon stays PM.
        assertEquals("Mitternacht", dayPeriod("de", LocalTime(0, 0), pattern = "b"))
        assertEquals("PM", dayPeriod("de", LocalTime(12, 0), pattern = "b"))
    }

    @Test
    fun fallsBackToAmPmWithoutRulesOrNames() {
        // Unknown language: root data, whose rules are plain am/pm.
        assertEquals("AM", dayPeriod("zz", LocalTime(9, 0)))
        assertEquals("PM", dayPeriod("zz", LocalTime(15, 0)))
        assertEquals("AM", dayPeriod("zz", LocalTime(0, 0), pattern = "b"))
    }

    // Traditional Chinese is the locale family whose standard time patterns
    // actually contain B (Bh:mm), so day periods show up in public output.
    @Test
    fun formatsChineseTimesWithFlexibleDayPeriods() {
        val zhHant = Locale.forLanguageTag("zh-Hant")
        assertEquals("凌晨2:05", LocalTime(2, 5).format(FormatStyle.SHORT, zhHant))
        assertEquals("清晨6:05", LocalTime(6, 5).format(FormatStyle.SHORT, zhHant))
        assertEquals("上午9:05:09", LocalTime(9, 5, 9).format(FormatStyle.MEDIUM, zhHant))
        assertEquals("中午12:05", LocalTime(12, 5).format(FormatStyle.SHORT, zhHant))
        assertEquals("下午3:05", LocalTime(15, 5).format(FormatStyle.SHORT, zhHant))
        assertEquals("晚上8:05", LocalTime(20, 5).format(FormatStyle.SHORT, zhHant))
        assertEquals("午夜12:00", LocalTime(0, 0).format(FormatStyle.SHORT, zhHant))
    }

    @Test
    fun stripsBracketedZoneFieldsFromChineseTimeStyles() {
        // The zh-Hant FULL pattern is "Bh:mm:ss [zzzz]"; dropping the zone must
        // also drop the brackets it lived in.
        val zhHant = Locale.forLanguageTag("zh-Hant")
        assertEquals("下午3:05:09", LocalTime(15, 5, 9).format(FormatStyle.FULL, zhHant))
        assertEquals("下午3:05:09", LocalTime(15, 5, 9).format(FormatStyle.LONG, zhHant))
    }
}
