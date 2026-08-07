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

package dev.carcara.kotlinx.locale.country

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToCountryNames
import dev.carcara.kotlinx.locale.country.cldr.CldrCountry
import dev.carcara.kotlinx.locale.country.platform.PlatformCountry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The platform country source, checked on every target this module builds for.
 *
 * Every test asserts something everywhere. Where behaviour depends on the host,
 * that is an if/else with assertions in both branches rather than an early
 * return, because a test that quietly does nothing on the four targets with no
 * locale data still passes there and reads as coverage it is not.
 */
class PlatformCountryTest {

    private val composed = FallbackCountryNames(primary = PlatformCountry, fallback = CldrCountry)

    private val en = Locale.of("en")

    // Runs identically everywhere ------------------------------------------------

    @Test
    fun theCompositionConformsBehaviourally() {
        composed.assertConformsToCountryNames(ConformanceTier.BEHAVIOURAL)
    }

    @Test
    fun theCompositionAnswersEverywhereEvenWhereThePlatformDoesNot() {
        // The property that makes the platform layer usable: whatever the host is
        // missing, the bundled source covers, and the caller sees one source that
        // always answers. True on the hosts with data and on the four without.
        for (tag in listOf("en", "pt-BR", "de", "ja", "ar-EG")) {
            val locale = Locale.forLanguageTag(tag)
            for (country in Country.entries) {
                val name = composed.displayName(country, locale)
                assertTrue(name.isNotBlank(), "$tag ${country.alpha2} was blank")
                assertTrue(name != country.alpha2, "$tag ${country.alpha2} fell back to its code")
            }
        }
    }

    @Test
    fun theCompositionAgreesWithTheBundledSourceInEnglishForTheMajorCountries() {
        // Every host and CLDR give the same English names for these, so this is an
        // exact assertion that holds on every target: on JVM, JS and Apple the
        // platform answers, on the other four the bundled source does, and the
        // result is the same either way.
        assertEquals("Brazil", composed.displayName(Country.forAlpha2("BR"), en))
        assertEquals("Germany", composed.displayName(Country.forAlpha2("DE"), en))
        assertEquals("Japan", composed.displayName(Country.forAlpha2("JP"), en))
    }

    // Host-dependent, asserted on both sides -------------------------------------

    @Test
    fun theSourceHonoursItsAvailabilityContract() {
        if (PlatformCountry.isAvailable) {
            for (alpha2 in listOf("BR", "DE", "JP", "US", "FR")) {
                val name = assertNotNull(
                    PlatformCountry.countryNameOrNull(alpha2, en),
                    "the platform has no English name for $alpha2",
                )
                // Never the code echoed back: that is what countryNameOrNull filters,
                // and it is what keeps a composing source honest.
                assertTrue(!name.equals(alpha2, ignoreCase = true), "$alpha2 came back as its own code")
            }
            assertEquals("Brazil", PlatformCountry.countryNameOrNull("BR", en))
        } else {
            // Linux, Windows, Android Native and WASI. A source that returned the
            // ISO code here would look like an answer and stop the fallback firing.
            assertEquals(null, PlatformCountry.countryNameOrNull("BR", en))
            assertEquals(null, PlatformCountry.countryNameOrNull("US", en))
            assertTrue(PlatformCountry.supportedLocales.isEmpty())
        }
    }

    @Test
    fun namesAreLocalizedWhereThePlatformHasThem() {
        val english = PlatformCountry.countryNameOrNull("DE", en)
        val german = PlatformCountry.countryNameOrNull("DE", Locale.of("de"))
        if (PlatformCountry.isAvailable) {
            assertNotNull(english)
            assertNotNull(german)
            assertTrue(english != german, "the platform returned '$english' for both en and de")
        } else {
            assertEquals(null, english)
            assertEquals(null, german)
        }
    }

    @Test
    fun anUnassignedCodeIsAnsweredByTheHostRatherThanEchoed() {
        // ZZ looks like a region code and nobody assigns it. CldrCountry returns
        // null. java.util.Locale returns a localized "Unknown Region", which the
        // echo filter cannot catch because it is a name rather than the code.
        //
        // Pinning this rather than smoothing it over: the two sources genuinely
        // disagree, and the disagreement is unreachable through the public API,
        // because every code that reaches a source comes from the Country enum and
        // all of those are assigned.
        val name = PlatformCountry.countryNameOrNull("ZZ", en)
        if (PlatformCountry.isAvailable) {
            assertTrue(
                name == null || name != "ZZ",
                "an unassigned code should miss or be named, never echoed: got '$name'",
            )
        } else {
            assertEquals(null, name)
        }
    }
}
