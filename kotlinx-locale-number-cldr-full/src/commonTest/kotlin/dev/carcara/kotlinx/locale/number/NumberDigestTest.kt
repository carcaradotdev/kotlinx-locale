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

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.assertDigestsMatch
import dev.carcara.kotlinx.locale.conformance.numberDigestSerialization
import dev.carcara.kotlinx.locale.conformance.pluralDigestSerialization
import dev.carcara.kotlinx.locale.number.cldr.CldrNumber
import dev.carcara.kotlinx.locale.number.cldr.CldrNumberPlurals
import dev.carcara.kotlinx.locale.number.conformance.numberDigests
import dev.carcara.kotlinx.locale.number.conformance.pluralDigests

/**
 * This target answers what the JVM answered, for every locale.
 *
 * Not an oracle. The expected values are this library's own output taken on the
 * JVM, so agreement says the twenty-four targets compute the same thing and
 * nothing about whether the thing is right. Correctness is `:conformance-icu`,
 * which can only run where ICU4J does.
 *
 * What this catches is the other failure: a `Regex`, a `Double`, a `lowercase`
 * or a hash iteration order behaving differently on one platform. The number
 * engine is where that is most likely, because it is the code that touches all
 * four.
 */
val NumberDigestTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("number formatting matches the JVM in every locale") {
        assertDigestsMatch("number", numberDigests) { tag ->
            CldrNumber.numberDigestSerialization(Locale.forLanguageTag(tag))
        }
    }

    test("plural selection matches the JVM in every locale") {
        assertDigestsMatch("plural", pluralDigests) { tag ->
            CldrNumberPlurals.pluralDigestSerialization(Locale.forLanguageTag(tag))
        }
    }
}
