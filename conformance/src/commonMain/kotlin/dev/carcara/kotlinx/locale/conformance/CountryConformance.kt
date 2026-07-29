package dev.carcara.kotlinx.locale.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.CountryNameSource
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.country.countryForDisplayNameOrNull
import dev.carcara.kotlinx.locale.country.displayName
import dev.carcara.kotlinx.locale.country.forAlpha2OrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs this source through the country conformance suite and fails the calling
 * test on the first disagreement.
 *
 * At [ConformanceTier.EXACT] every name is compared against ICU's; at
 * [ConformanceTier.BEHAVIOURAL] only against what the API promises regardless
 * of where the data came from.
 */
public fun CountryNameSource.assertConformsToCountryNames(tier: ConformanceTier) {
    assertTrue(supportedLocales.isNotEmpty(), "a source that supports no locale answers nothing")

    val english = Locale.of("en")
    assertTrue(english in supportedLocales, "every source is expected to carry English")

    if (tier == ConformanceTier.EXACT) assertMatchesIcuCountryNames()
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
