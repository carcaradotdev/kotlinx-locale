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

package dev.carcara.kotlinx.locale.country.conformance

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.CountryNameSource
import dev.carcara.kotlinx.locale.test.assertFailsWith

/**
 * The negative case for the ICU comparison, which moved here with the golden.
 *
 * `conformance-test-suite` used to own this: its exact tier called the ICU
 * comparison, so a well-shaped source that was not CLDR failed there. The
 * comparison is now next to the fixture it reads, and so is the proof that it
 * rejects something. Without this, `assertMatchesIcuCountryNames` could be
 * gutted to a no-op and every conformance test in the build would stay green.
 */
val CountryIcuConformanceRejection by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("rejects a well-shaped source that is not CLDR") {
        val plausible = object : CountryNameSource {
            override val supportedLocales: Set<Locale> = setOf(Locale.of("en"))
            override fun countryNameOrNull(alpha2: String, locale: Locale): String = "Country $alpha2"
        }
        assertFailsWith<AssertionError> { plausible.assertMatchesIcuCountryNames() }
    }
}
