package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.Locale

import dev.carcara.kotlinx.locale.datetime.internal.localeDataFor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-checks the CLDR-generated runtime data against patterns and names
 * extracted from the official ICU repository (a fully independent encoding of
 * the same upstream data). Fields ICU does not define for a locale are null
 * in the golden entry and skipped.
 */
class IcuGoldenTest {

    // The two sources are point releases of the same upstream data and can
    // disagree on which non-breaking space variant they use (U+00A0 vs U+202F);
    // normalize those before comparing.
    private fun String.normalized() = replace(' ', ' ').replace(' ', ' ')
    private fun List<String>.normalized() = map { it.normalized() }

    @Test
    fun runtimeDataMatchesIcu() {
        for (golden in icuGoldenData) {
            val data = localeDataFor(Locale.forLanguageTag(golden.tag))
            golden.dateFormats?.let {
                assertEquals(it.normalized(), data.dateFormats.normalized(), "${golden.tag} dateFormats")
            }
            golden.timeFormats?.let {
                assertEquals(it.normalized(), data.timeFormats.normalized(), "${golden.tag} timeFormats")
            }
            golden.glueFormats?.let {
                assertEquals(it.normalized(), data.glueFormats.normalized(), "${golden.tag} glueFormats")
            }
            golden.monthsWide?.let {
                assertEquals(it.normalized(), data.monthsWide.normalized(), "${golden.tag} monthsWide")
            }
            golden.monthsAbbr?.let {
                assertEquals(it.normalized(), data.monthsAbbr.normalized(), "${golden.tag} monthsAbbr")
            }
            golden.daysWide?.let {
                assertEquals(it.normalized(), data.daysWide.normalized(), "${golden.tag} daysWide")
            }
            golden.daysAbbr?.let {
                assertEquals(it.normalized(), data.daysAbbr.normalized(), "${golden.tag} daysAbbr")
            }
            golden.am?.let { assertEquals(it.normalized(), data.am.normalized(), "${golden.tag} am") }
            golden.pm?.let { assertEquals(it.normalized(), data.pm.normalized(), "${golden.tag} pm") }
        }
    }
}
