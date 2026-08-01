package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.relative.CldrRelativeTime
import dev.carcara.kotlinx.locale.datetime.cldr.relative.formatRelative
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val EN = Locale.of("en")
private val CS = Locale.of("cs")

class CldrRelativeTimeTest {

    @Test
    fun prefersTheWordWhereTheLocaleHasOne() {
        assertEquals("yesterday", (-1L).formatRelative(RelativeTimeUnit.DAY, locale = EN))
        assertEquals("tomorrow", 1L.formatRelative(RelativeTimeUnit.DAY, locale = EN))
        assertEquals("today", 0L.formatRelative(RelativeTimeUnit.DAY, locale = EN))
        assertEquals("včera", (-1L).formatRelative(RelativeTimeUnit.DAY, locale = CS))
        assertEquals("zítra", 1L.formatRelative(RelativeTimeUnit.DAY, locale = CS))
        assertEquals("předevčírem", (-2L).formatRelative(RelativeTimeUnit.DAY, locale = CS))
    }

    @Test
    fun countsWhenAskedTo() {
        assertEquals("1 day ago", (-1L).formatRelative(RelativeTimeUnit.DAY, numbering = RelativeTimeNumbering.ALWAYS, locale = EN))
        assertEquals("in 1 day", 1L.formatRelative(RelativeTimeUnit.DAY, numbering = RelativeTimeNumbering.ALWAYS, locale = EN))
    }

    @Test
    fun czechPicksAmongItsFourPluralForms() {
        // one, few, many and other are four different words, and this is what a
        // hand-rolled ladder that divides by seven gets wrong.
        assertEquals("před 1 dnem", (-1L).formatRelative(RelativeTimeUnit.DAY, numbering = RelativeTimeNumbering.ALWAYS, locale = CS))
        assertEquals("před 3 dny", (-3L).formatRelative(RelativeTimeUnit.DAY, locale = CS))
        assertEquals("před 10 dny", (-10L).formatRelative(RelativeTimeUnit.DAY, locale = CS))
        assertEquals("za 3 dny", 3L.formatRelative(RelativeTimeUnit.DAY, locale = CS))
        assertEquals("za 10 dní", 10L.formatRelative(RelativeTimeUnit.DAY, locale = CS))
    }

    @Test
    fun theWidthsFallBackToTheBase() {
        for (style in RelativeTimeStyle.entries) {
            assertTrue(
                (-3L).formatRelative(RelativeTimeUnit.HOUR, style, locale = EN).isNotBlank(),
                "$style rendered nothing",
            )
        }
        assertEquals("3 hr. ago", (-3L).formatRelative(RelativeTimeUnit.HOUR, RelativeTimeStyle.SHORT, locale = EN))
    }

    @Test
    fun everyLocaleAndUnitAnswers() {
        var checked = 0
        for (locale in CldrRelativeTime.supportedLocales) {
            for (unit in RelativeTimeUnit.entries) {
                assertTrue((-3L).formatRelative(unit, locale = locale).isNotBlank(), "$locale $unit")
                checked++
            }
        }
        assertTrue(checked > 8000, "expected every locale and unit, got $checked")
    }
}
