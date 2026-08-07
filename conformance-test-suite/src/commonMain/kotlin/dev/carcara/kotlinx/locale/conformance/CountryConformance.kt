/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.carcara.kotlinx.locale.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.CountryNameSource
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.country.countryForDisplayNameOrNull
import dev.carcara.kotlinx.locale.country.displayName
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNotNull
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * Runs this source through the country conformance suite and fails the calling
 * test on the first disagreement.
 *
 * Both tiers get the shape checks. The comparison against ICU's own names is
 * not here: it needs the golden, and the golden lives in the module that owns
 * the table it describes. `country-cldr-full` runs it as its own case.
 */
public fun CountryNameSource.assertConformsToCountryNames(tier: ConformanceTier) {
    val english = Locale.of("en")

    // Only the exact tier can require these. A source over Intl answers every
    // lookup and still enumerates nothing, because ECMA-402 offers no way to ask
    // what it supports, so its coverage cannot be asserted, only exercised.
    if (tier == ConformanceTier.EXACT) {
        assertTrue(supportedLocales.isNotEmpty(), "a CLDR-backed source is expected to enumerate its locales")
        assertTrue(english in supportedLocales, "a CLDR-backed source is expected to carry English")
    }
    assertNamesAreWellShaped(english)
    assertNamesReverseLookUp(english)
    assertUnknownLocalesStillAnswer()
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
