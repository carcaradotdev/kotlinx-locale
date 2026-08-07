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

package dev.carcara.kotlinx.locale.number

import dev.carcara.kotlinx.locale.conformance.assertConformsToCldrPluralSamples
import dev.carcara.kotlinx.locale.conformance.assertConformsToIcuNumbers
import dev.carcara.kotlinx.locale.conformance.assertConformsToIcuPlurals
import dev.carcara.kotlinx.locale.conformance.assertNumbersAreWellShaped
import dev.carcara.kotlinx.locale.number.cldr.CldrNumber
import dev.carcara.kotlinx.locale.number.cldr.CldrNumberPlurals
import kotlin.test.Test

/**
 * The bundled source is a second encoding of the data CLDR ships, so it is held
 * to CLDR's own samples rather than only to the shape of its answers.
 */
class CldrNumberConformanceTest {

    @Test
    fun pluralRulesAgreeWithCldrsOwnSamples() = CldrNumberPlurals.assertConformsToCldrPluralSamples()

    @Test
    fun numbersAreWellShaped() = CldrNumber.assertNumbersAreWellShaped()

    @Test
    fun pluralRulesAlsoAgreeWithIcu() = CldrNumberPlurals.assertConformsToIcuPlurals()

    @Test
    fun formattingAgreesWithIcu() = CldrNumber.assertConformsToIcuNumbers()
}
