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

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.assertDigestsMatch
import dev.carcara.kotlinx.locale.conformance.currencyDigestSerialization
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.conformance.currencyFormatDigests

/**
 * This target formats currency the way the JVM does, for every locale.
 *
 * See `NumberDigestTest` for why a digest rather than a golden, and for what
 * agreement here does and does not prove.
 *
 * Currency formatting is worth its own set because it is the number engine plus
 * a pattern with affixes, and the affixes are where the no-break spaces live.
 * Those are exactly the characters the targets disagree about.
 */
val CurrencyDigestTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("currency formatting matches the JVM in every locale") {
        assertDigestsMatch("currency-format", currencyFormatDigests) { tag ->
            CldrCurrency.currencyDigestSerialization(Locale.forLanguageTag(tag))
        }
    }
}
