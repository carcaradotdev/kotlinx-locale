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

package dev.carcara.kotlinx.locale.phone

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.phone.conformance.assertConformsToLibPhoneNumber
import dev.carcara.kotlinx.locale.phone.conformance.assertParsesLikeLibPhoneNumber
import dev.carcara.kotlinx.locale.phone.metadata.PhoneNumbers

/** The bundled metadata is a second encoding of libphonenumber's, so it answers to it. */
val PhoneConformanceTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("agreesWithLibPhoneNumber") {
        PhoneNumbers.assertConformsToLibPhoneNumber()
    }

    test("parsesTheAwkwardInputsTheSameWay") {
        PhoneNumbers.assertParsesLikeLibPhoneNumber()
    }
}
