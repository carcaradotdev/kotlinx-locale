package dev.carcara.kotlinx.locale.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.CountryNameSource
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.country.countryForDisplayNameOrNull
import dev.carcara.kotlinx.locale.country.displayName
import dev.carcara.kotlinx.locale.country.flagEmoji
import dev.carcara.kotlinx.locale.country.forAlpha2OrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

/**
 * Runs this source through the country conformance suite and fails the calling
 * test on the first disagreement.
 *
 * At [ConformanceTier.EXACT] every name is compared against ICU's; at
 * [ConformanceTier.BEHAVIOURAL] only against what the API promises regardless
 * of where the data came from.
 */
public fun CountryNameSource.assertConformsToCountryNames(tier: ConformanceTier) {
    val english = Locale.of("en")

    // Only the exact tier can require these. A source over Intl answers every
    // lookup and still enumerates nothing, because ECMA-402 offers no way to ask
    // what it supports, so its coverage cannot be asserted, only exercised.
    if (tier == ConformanceTier.EXACT) {
        assertTrue(supportedLocales.isNotEmpty(), "a CLDR-backed source is expected to enumerate its locales")
        assertTrue(english in supportedLocales, "a CLDR-backed source is expected to carry English")
        assertMatchesIcuCountryNames()
    }
    assertNamesAreWellShaped(english)
    assertNamesReverseLookUp(english)
    assertUnknownLocalesStillAnswer()
}

private fun CountryNameSource.assertMatchesIcuCountryNames() {
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

private fun CountryNameSource.assertNamesAreWellShaped(english: Locale) {
    for (country in Country.entries) {
        val name = displayName(country, english)
        assertTrue(name.isNotBlank(), "${country.alpha2} was blank in en")
        // English is the one locale where falling back to the code means the
        // table is missing an entry rather than the locale being unsupported.
        assertTrue(name != country.alpha2, "${country.alpha2} fell back to its code in en")
    }
}

private fun CountryNameSource.assertNamesReverseLookUp(english: Locale) {
    // Some locales genuinely give two countries the same name, so the general
    // contract is that the reverse lookup returns a country carrying exactly
    // the name asked for. English is the exception worth being strict about:
    // if two countries share an English name, one of them is unreachable.
    for (country in Country.entries) {
        val name = displayName(country, english)
        val found = countryForDisplayNameOrNull(name, english)
        assertNotNull(found, "'$name' found nothing")
        assertEquals(country, found, "'$name' is not unique in English, so it reverses to the wrong country")
    }
    assertEquals(null, countryForDisplayNameOrNull("", english), "the empty name matches nothing")
    assertEquals(null, countryForDisplayNameOrNull("Atlantis", english), "an invented name matches nothing")
}

private fun CountryNameSource.assertUnknownLocalesStillAnswer() {
    // A syntactically valid language nobody has data for. What comes back
    // depends on the source — the ISO code for a bundled one, the configured
    // fallback locale's name for a generated one — but it is never nothing.
    val unknown = Locale.of("zz")
    for (country in Country.entries.take(10)) {
        assertTrue(displayName(country, unknown).isNotBlank(), "${country.alpha2} was blank in an unsupported locale")
    }
}
