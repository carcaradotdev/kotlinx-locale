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

package dev.carcara.kotlinx.locale.currency

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToCurrencyFormats
import dev.carcara.kotlinx.locale.conformance.assertConformsToCurrencyNames
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.conformance.assertCurrencyNumericCodesMatchIcu
import dev.carcara.kotlinx.locale.currency.conformance.assertMatchesIcuCurrencyNames

/**
 * The bundled source is a second encoding of the data ICU encodes, so it is
 * held to the exact tier for names and symbols. Formatted output has no ICU
 * fixture to compare against and is checked by round-tripping instead.
 */
val CldrCurrencyConformanceTest by matrixSuite {

    test("names conform to the source contract at the exact tier") {
        CldrCurrency.assertConformsToCurrencyNames(ConformanceTier.EXACT)
    }

    test("names and symbols match ICU") {
        CldrCurrency.assertMatchesIcuCurrencyNames()
    }

    test("formatting conforms") {
        CldrCurrency.assertConformsToCurrencyFormats(ConformanceTier.EXACT)
    }

    test("numeric codes match ICU") {
        assertCurrencyNumericCodesMatchIcu()
    }
}
