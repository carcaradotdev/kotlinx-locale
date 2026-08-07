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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-checks the generated ISO 3166-1 data against the JDK's own tables —
 * a third independent source next to CLDR and ICU, available on JVM only.
 */
class JdkCountryParityTest {

    @Test
    fun alpha2SetMatchesTheJdk() {
        val jdk = java.util.Locale.getISOCountries().toSortedSet()
        val ours = Country.entries.map(Country::alpha2).toSortedSet()
        assertEquals(jdk, ours)
    }

    @Test
    fun alpha3CodesMatchTheJdk() {
        for (country in Country.entries) {
            assertEquals(
                java.util.Locale.of("", country.alpha2).isO3Country,
                country.alpha3,
                country.alpha2,
            )
        }
    }
}
