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

import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToCountryNames
import dev.carcara.kotlinx.locale.conformance.assertEveryCountryHasAnRgiFlag
import dev.carcara.kotlinx.locale.country.cldr.CldrCountry
import kotlin.test.Test

/**
 * The bundled source is a second encoding of the data ICU encodes, so it is
 * held to the exact tier: every name matches ICU byte for byte.
 */
class CldrCountryConformanceTest {

    @Test
    fun conformsExactly() = CldrCountry.assertConformsToCountryNames(ConformanceTier.EXACT)

    /**
     * Flags are not locale data and not this source's, but this is where the
     * country entry set is on the classpath with the conformance fixtures.
     */
    @Test
    fun everyCountryHasAFlagUnicodeRecommends() = assertEveryCountryHasAnRgiFlag()
}
