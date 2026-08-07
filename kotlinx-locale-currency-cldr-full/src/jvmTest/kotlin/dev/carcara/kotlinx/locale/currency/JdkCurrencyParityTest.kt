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
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * Cross-checks the generated ISO 4217 data against the JDK's currency table — a
 * third independent source next to the vendored ISO lists and ICU.
 *
 * Held strictly for the currencies still in use, where the two tables describe
 * the same live standard and any disagreement is a bug in one of them.
 *
 * Not held for the withdrawn ones. ISO's own list three omits the minor units
 * field entirely, so this library takes CLDR's currency fractions instead, and
 * the JDK takes its own historical table. The two genuinely disagree about a
 * handful of long-gone currencies — the JDK says the Belgian franc had no minor
 * units where CLDR says two — and neither is authoritative for a library whose
 * upstream is CLDR. The disagreements are counted rather than asserted, so a
 * sudden jump is still visible.
 */
val JdkCurrencyParityTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("active Currencies Match The Jdk") {
        var skipped = 0
        val mismatches = ArrayList<String>()
        for (currency in Currency.active) {
            val jdk = try {
                java.util.Currency.getInstance(currency.code)
            } catch (e: IllegalArgumentException) {
                skipped++
                continue
            }
            if (jdk.numericCode != currency.numericCode) {
                mismatches.add("${currency.code}: numeric ${currency.numericCode} vs JDK ${jdk.numericCode}")
            }
            if (jdk.defaultFractionDigits != currency.defaultFractionDigits) {
                mismatches.add(
                    "${currency.code}: digits ${currency.defaultFractionDigits} " +
                        "vs JDK ${jdk.defaultFractionDigits}",
                )
            }
        }
        assertTrue(mismatches.isEmpty(), mismatches.joinToString("\n"))
        assertTrue(skipped <= 5, "JDK was missing $skipped currencies; its data may be stale")
    }

    test("withdrawn Currencies Diverge From The Jdk Only Where The Sources Do") {
        val divergent = ArrayList<String>()
        var compared = 0
        for (currency in Currency.entries) {
            if (currency.isActive) continue
            val jdk = try {
                java.util.Currency.getInstance(currency.code)
            } catch (e: IllegalArgumentException) {
                continue
            }
            compared++
            if (jdk.defaultFractionDigits != currency.defaultFractionDigits ||
                (jdk.numericCode != currency.numericCode && currency.numericCode >= 0)
            ) {
                divergent += currency.code
            }
        }
        // The JDK carries about half the withdrawn set; the rest predate its table.
        assertTrue(compared > 40, "expected the JDK to know a good share of the withdrawn codes, it knew $compared")
        assertTrue(
            divergent.size <= 20,
            "CLDR and the JDK now disagree about ${divergent.size} withdrawn currencies " +
                "(${divergent.joinToString()}), which is more than the historical noise this allows for",
        )
    }
}
