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

@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.currency

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.conformance.icuCurrencyGoldenData
import dev.carcara.kotlinx.locale.test.assertEquals

/**
 * Cross-checks the number-formatting tables against ICU's independently encoded
 * resource bundles.
 *
 * Symbols and display names are checked by the shared conformance suite, which
 * any source can run. These are the raw tables behind the formatter, reachable
 * only from inside the module that owns them, and no source interface exposes
 * them because no platform could implement one that did.
 */
val IcuCurrencyGoldenTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    fun String.normalized() = replace(' ', ' ').replace(' ', ' ')

    test("runtimeNumberDataMatchesIcu") {
        for (golden in icuCurrencyGoldenData) {
            val format = currencyFormatFor(Locale.forLanguageTag(golden.tag))
            golden.decimal?.let {
                assertEquals(it.normalized(), format.decimal.normalized(), "${golden.tag} decimal")
            }
            golden.group?.let {
                assertEquals(it.normalized(), format.group.normalized(), "${golden.tag} group")
            }
            golden.currencyPattern?.let {
                assertEquals(
                    it.normalized(),
                    format.standardPattern.normalized(),
                    "${golden.tag} currency pattern",
                )
            }
        }
    }
}
