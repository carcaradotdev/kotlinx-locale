package dev.carcara.kotlinx.locale.datetime.cldr.intervals

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.intervals.conformance.icuIntervalGolden
import dev.carcara.kotlinx.locale.datetime.cldr.intervals.conformance.icuIntervalGoldenCases
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntervalFormatTest {

    private val en = Locale.forLanguageTag("en")

    /**
     * CLDR and ICU disagree about which invisible space they write, and the
     * difference is not a formatting decision. Same fold the shared conformance
     * assertions apply.
     */
    private fun String.spaces(): String = map { if (it.isWhitespace()) ' ' else it }.joinToString("")

    private fun date(text: String): LocalDate {
        val (y, m, d) = text.split('-').map(String::toInt)
        return LocalDate(y, m, d)
    }

    @Test
    fun theFourCasesCollapseWhatTheyShare() {
        assertEquals("Jul 22, 2026", intervalFormat(date("2026-07-22"), date("2026-07-22"), "yMMMd", en).spaces())
        assertEquals("Jul 18 – 22, 2026", intervalFormat(date("2026-07-18"), date("2026-07-22"), "yMMMd", en).spaces())
        assertEquals("May 18 – Jul 22, 2026", intervalFormat(date("2026-05-18"), date("2026-07-22"), "yMMMd", en).spaces())
        assertEquals("May 18, 2025 – Jul 22, 2026", intervalFormat(date("2025-05-18"), date("2026-07-22"), "yMMMd", en).spaces())
    }

    @Test
    fun anUnrenderableSkeletonFallsBackToTheIso8601IntervalForm() {
        // Week-of-year is not a field this library renders, so the matcher
        // refuses the skeleton and there is no locale answer to give. ISO
        // 8601-1:2019 clause 3.2.6 writes an interval as <start>/<end>, so that
        // is what comes back, rather than an en dash borrowed from English for a
        // locale we just failed to find data for.
        assertEquals(
            "2026-07-18/2026-07-22",
            intervalFormat(date("2026-07-18"), date("2026-07-22"), "w", en),
        )
        assertEquals(
            "2026-07-18/2026-07-22",
            intervalFormat(date("2026-07-18"), date("2026-07-22"), "w", Locale.forLanguageTag("ja")),
        )
    }

    @Test
    fun anIdenticalPairFormatsOnce() {
        // The case most likely to come out as the same text twice with a dash.
        val once = intervalFormat(date("2026-07-22"), date("2026-07-22"), "yMMMd", en)
        assertTrue('–' !in once && '-' !in once, "an identical pair should not be joined: $once")
    }

    @Test
    fun everyLocaleAgreesWithIcu() {
        val mismatches = ArrayList<String>()
        var compared = 0
        for ((tag, expected) in icuIntervalGolden) {
            val locale = Locale.forLanguageTag(tag)
            for ((index, case) in icuIntervalGoldenCases.withIndex()) {
                val (skeleton, start, end) = case
                val actual = intervalFormat(date(start), date(end), skeleton, locale)
                compared++
                if (actual.spaces() != expected[index].spaces()) {
                    mismatches += "$tag $skeleton $start..$end: expected '${expected[index]}', got '$actual'"
                }
            }
        }
        assertTrue(compared > 10_000, "the golden shrank to $compared comparisons")
        assertTrue(
            mismatches.isEmpty(),
            "${mismatches.size} of $compared disagree with ICU across " +
                "${mismatches.map { it.substringBefore(' ') }.toSet().size} locales:\n" +
                mismatches.take(25).joinToString("\n"),
        )
    }
}
