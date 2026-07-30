package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToDateTimeFormats
import dev.carcara.kotlinx.locale.datetime.cldr.CldrDateTime
import dev.carcara.kotlinx.locale.datetime.platform.PlatformDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The platform datetime source, and the timezone trap it exists to avoid.
 */
class PlatformDateTimeTest {

    private val composed = FallbackDateTimeFormats(primary = PlatformDateTime, fallback = CldrDateTime)

    private val en = Locale.of("en")
    private val date = LocalDate(2026, 7, 27)
    private val time = LocalTime(15, 5, 9)

    @Test
    fun theCompositionConformsBehaviourally() {
        composed.assertConformsToDateTimeFormats(ConformanceTier.BEHAVIOURAL)
    }

    @Test
    fun thePlatformSourceConformsBehaviourallyWhereItHasData() {
        if (!PlatformDateTime.isAvailable) return
        PlatformDateTime.assertConformsToDateTimeFormats(ConformanceTier.BEHAVIOURAL)
    }

    @Test
    fun theUnavailableTargetsSaySoRatherThanAnsweringBadly() {
        if (PlatformDateTime.isAvailable) return
        assertEquals(null, PlatformDateTime.formatDateOrNull(date, FormatStyle.LONG, en))
        assertEquals(null, PlatformDateTime.monthNameOrNull(7, TextStyle.FULL, en))
        assertTrue(PlatformDateTime.supportedLocales.isEmpty())
    }

    @Test
    fun theDayThatGoesInIsTheDayThatComesOut() {
        if (!PlatformDateTime.isAvailable) return
        // The trap this guards: a LocalDate has no zone, and the platform
        // formatters take an instant plus a zone. Formatted in the host's zone,
        // 2026-07-27 renders as the 26th or the 28th depending on where the
        // machine is, which is a bug that only some users ever see. Every actual
        // formats in UTC for exactly this reason.
        for (style in listOf(FormatStyle.LONG, FormatStyle.MEDIUM, FormatStyle.SHORT)) {
            val formatted = assertNotNull(PlatformDateTime.formatDateOrNull(date, style, en))
            assertTrue("27" in formatted, "$style lost or shifted the day: '$formatted'")
            val year = if (style == FormatStyle.SHORT) "26" else "2026"
            assertTrue(year in formatted, "$style lost the year: '$formatted'")
        }
    }

    @Test
    fun theHourThatGoesInIsTheHourThatComesOut() {
        if (!PlatformDateTime.isAvailable) return
        val formatted = assertNotNull(PlatformDateTime.formatTimeOrNull(time, FormatStyle.MEDIUM, en))
        assertTrue("05" in formatted || "5" in formatted, "lost the minutes: '$formatted'")
        // 15:05 in a 12-hour locale is 3:05, so either form is correct; what must
        // not happen is the hour drifting with the machine's zone.
        assertTrue("3" in formatted || "15" in formatted, "lost or shifted the hour: '$formatted'")
    }

    @Test
    fun theFourLengthsDifferFromEachOther() {
        if (!PlatformDateTime.isAvailable) return
        val rendered = FormatStyle.entries.map { assertNotNull(PlatformDateTime.formatDateOrNull(date, it, en)) }
        // FULL and LONG can coincide in some locales, but all four collapsing to
        // one string would mean the style is being ignored.
        assertTrue(rendered.toSet().size > 1, "every length rendered the same string: $rendered")
        assertTrue(rendered.first().length >= rendered.last().length, "FULL was shorter than SHORT: $rendered")
    }

    @Test
    fun theNamesAreLocalizedAndNotJustEnglish() {
        if (!PlatformDateTime.isAvailable) return
        assertEquals("July", PlatformDateTime.monthNameOrNull(7, TextStyle.FULL, en))
        assertEquals("Monday", PlatformDateTime.dayOfWeekNameOrNull(1, TextStyle.FULL, en))

        val german = PlatformDateTime.monthNameOrNull(7, TextStyle.FULL, Locale.of("de"))
        assertEquals("Juli", german)

        // Sunday is ISO 7, and Foundation indexes weekdays from Sunday, so this is
        // the off-by-one that mapping would get wrong.
        assertEquals("Sunday", PlatformDateTime.dayOfWeekNameOrNull(7, TextStyle.FULL, en))
    }

    @Test
    fun theGluedDateTimeCarriesBothHalves() {
        if (!PlatformDateTime.isAvailable) return
        val moment = LocalDateTime(date, time)
        val glued = assertNotNull(
            PlatformDateTime.formatDateTimeOrNull(moment, FormatStyle.MEDIUM, FormatStyle.SHORT, en),
        )
        assertTrue("27" in glued, "the date half is missing: '$glued'")
        assertTrue("05" in glued || "5" in glued, "the time half is missing: '$glued'")
    }

    @Test
    fun anOutOfRangeMonthOrWeekdayIsRefused() {
        assertEquals(null, PlatformDateTime.monthNameOrNull(0, TextStyle.FULL, en))
        assertEquals(null, PlatformDateTime.monthNameOrNull(13, TextStyle.FULL, en))
        assertEquals(null, PlatformDateTime.dayOfWeekNameOrNull(0, TextStyle.FULL, en))
        assertEquals(null, PlatformDateTime.dayOfWeekNameOrNull(8, TextStyle.FULL, en))
    }
}
