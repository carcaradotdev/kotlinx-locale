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
import dev.carcara.kotlinx.locale.country.CountryNameSource
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The suite is only worth having if it fails on a source that is wrong, so
 * that is what these check. They also pin down what the behavioural tier costs
 * a source that carries no CLDR data at all, which is the tier the platform
 * layer will be held to.
 */
class ConformanceSuiteTest {

    private fun source(name: (String) -> String?) = object : CountryNameSource {
        override val supportedLocales: Set<Locale> = setOf(Locale.of("en"))
        override fun countryNameOrNull(alpha2: String, locale: Locale): String? = name(alpha2)
    }

    @Test
    fun acceptsANonCldrSourceThatIsWellShaped() {
        source { "Country $it" }.assertConformsToCountryNames(ConformanceTier.BEHAVIOURAL)
    }

    @Test
    fun rejectsASourceThatNamesNothing() {
        assertFailsWith<AssertionError> {
            source { null }.assertConformsToCountryNames(ConformanceTier.BEHAVIOURAL)
        }
    }

    @Test
    fun rejectsASourceThatGivesEveryCountryTheSameName() {
        // Reverse lookup would answer, but with a country that is not the one
        // asked about, which is exactly the bug the round trip exists to catch.
        assertFailsWith<AssertionError> {
            source { "Somewhere" }.assertConformsToCountryNames(ConformanceTier.BEHAVIOURAL)
        }
    }

    @Test
    fun acceptsASourceThatAnswersWithoutEnumerating() {
        // What a source over Intl looks like: every lookup works, the coverage
        // cannot be listed. The behavioural tier has to allow this, or the
        // platform layer could never pass it.
        val opaque = object : CountryNameSource {
            override val supportedLocales: Set<Locale> = emptySet()
            override fun countryNameOrNull(alpha2: String, locale: Locale): String = "Country $alpha2"
        }
        opaque.assertConformsToCountryNames(ConformanceTier.BEHAVIOURAL)
        // The exact tier still wants a source that can describe itself.
        assertFailsWith<AssertionError> { opaque.assertConformsToCountryNames(ConformanceTier.EXACT) }
    }

    @Test
    fun rejectsANonCldrSourceAtTheExactTier() {
        assertFailsWith<AssertionError> {
            source { "Country $it" }.assertConformsToCountryNames(ConformanceTier.EXACT)
        }
    }
}
