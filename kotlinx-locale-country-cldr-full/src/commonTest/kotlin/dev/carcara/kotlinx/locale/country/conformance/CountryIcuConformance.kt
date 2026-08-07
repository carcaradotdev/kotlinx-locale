package dev.carcara.kotlinx.locale.country.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.normalizedSpaces
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.CountryNameSource
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.country.displayName
import dev.carcara.kotlinx.locale.country.flagEmoji
import dev.carcara.kotlinx.locale.country.forAlpha2OrNull
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNotNull
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * The comparisons that need a golden, and so live next to the one they read.
 *
 * These were in `conformance-test-suite` until the fixtures moved out of it. The
 * shared module is a project dependency of six modules and compiles into every
 * one of their test binaries, so a golden parked there was linked into targets
 * that had no use for it. What stayed behind is the contract a `CountryNameSource`
 * owes whatever its data came from, which is the part a platform source also has
 * to satisfy. What moved here is the part that is only meaningful for the table
 * this module ships.
 */

/**
 * Holds this source's names to ICU's for the golden locales.
 *
 * Thirty locales, which is a sample rather than coverage: `:conformance-icu`
 * asks ICU the same question for all eleven hundred. This one is what runs on
 * the twenty-four targets ICU4J cannot, and what gives a failure a diff a person
 * can read.
 */
public fun CountryNameSource.assertMatchesIcuCountryNames() {
    assertTrue(icuCountryGoldenData.size >= 25, "expected the full golden locale set")
    for (golden in icuCountryGoldenData) {
        val locale = Locale.forLanguageTag(golden.tag)
        assertTrue(golden.names.isNotEmpty(), "${golden.tag} has no golden names")
        for ((alpha2, icuName) in golden.names) {
            val country = assertNotNull(Country.forAlpha2OrNull(alpha2), "$alpha2 is not in this build's entry set")
            assertEquals(
                icuName.normalizedSpaces(),
                displayName(country, locale).normalizedSpaces(),
                "${golden.tag} $alpha2",
            )
        }
    }
}

/**
 * Holds `Country.flagEmoji` to the RGI flag sequences of UTS #51.
 *
 * Not on a source, because a flag is not locale data: it is arithmetic on the
 * alpha-2 code. What is worth checking is that the arithmetic lands on a
 * sequence Unicode recommends, and that the surrogate pair it builds is right on
 * whichever target this runs on.
 */
public fun assertEveryCountryHasAnRgiFlag() {
    assertTrue(rgiFlagRegionCodes.size > 240, "expected the full RGI flag set, got ${rgiFlagRegionCodes.size}")
    for (country in Country.entries) {
        assertTrue(
            country.alpha2 in rgiFlagRegionCodes,
            "${country.alpha2} has no RGI flag sequence in Emoji $RGI_EMOJI_VERSION",
        )
        val flag = country.flagEmoji
        // Two astral code points, so four UTF-16 units on every target.
        assertEquals(4, flag.length, "${country.alpha2} did not build two regional indicators")
        val codePoints = listOf(flag.codePointAtIndex(0), flag.codePointAtIndex(2))
        assertEquals(
            country.alpha2,
            codePoints.map { 'A' + (it - 0x1F1E6) }.joinToString(""),
            "${country.alpha2} built a sequence that decodes to something else",
        )
    }
}

private fun String.codePointAtIndex(index: Int): Int {
    val high = this[index].code
    val low = this[index + 1].code
    return 0x10000 + ((high - 0xD800) shl 10) + (low - 0xDC00)
}
