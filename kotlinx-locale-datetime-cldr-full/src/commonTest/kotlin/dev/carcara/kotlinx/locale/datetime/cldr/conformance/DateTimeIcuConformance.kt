package dev.carcara.kotlinx.locale.datetime.cldr.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.normalizedSpaces
import dev.carcara.kotlinx.locale.datetime.DateTimeFormatSource
import dev.carcara.kotlinx.locale.datetime.NameContext
import dev.carcara.kotlinx.locale.datetime.TextStyle
import dev.carcara.kotlinx.locale.datetime.displayName
import dev.carcara.kotlinx.locale.test.assertEquals
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month

/**
 * The month and weekday comparison that needs a golden, next to the golden.
 *
 * See `CountryIcuConformance` for why this is no longer in the shared module.
 * Only the names are comparable here: the fixtures hold CLDR's *patterns* and
 * the `DateTimeFormatSource` interface deliberately does not hand one out, since
 * no platform source can.
 */
public fun DateTimeFormatSource.assertMatchesIcuCalendarNames() {
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
        // The stand-alone context, which ICU stores under the same keys and
        // which is where a calendar header and a month picker read from.
        golden.monthsStandaloneWide?.forEachIndexed { index, icuName ->
            assertEquals(
                icuName.normalizedSpaces(),
                displayName(Month(index + 1), TextStyle.FULL, NameContext.STANDALONE, locale).normalizedSpaces(),
                "${golden.tag} monthsStandaloneWide[$index]",
            )
        }
        golden.monthsStandaloneAbbr?.forEachIndexed { index, icuName ->
            assertEquals(
                icuName.normalizedSpaces(),
                displayName(Month(index + 1), TextStyle.ABBREVIATED, NameContext.STANDALONE, locale).normalizedSpaces(),
                "${golden.tag} monthsStandaloneAbbr[$index]",
            )
        }
        golden.daysStandaloneWide?.forEachIndexed { index, icuName ->
            assertEquals(
                icuName.normalizedSpaces(),
                displayName(DayOfWeek(index + 1), TextStyle.FULL, NameContext.STANDALONE, locale).normalizedSpaces(),
                "${golden.tag} daysStandaloneWide[$index]",
            )
        }
        golden.daysStandaloneAbbr?.forEachIndexed { index, icuName ->
            assertEquals(
                icuName.normalizedSpaces(),
                displayName(DayOfWeek(index + 1), TextStyle.ABBREVIATED, NameContext.STANDALONE, locale).normalizedSpaces(),
                "${golden.tag} daysStandaloneAbbr[$index]",
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
