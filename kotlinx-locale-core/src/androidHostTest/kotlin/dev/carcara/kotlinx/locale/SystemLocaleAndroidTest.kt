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

package dev.carcara.kotlinx.locale

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNull

/**
 * A host test runs off a device, and the stub `android.jar` throws rather than
 * answering, so `DateFormat.is24HourFormat` is never reached here. The mapping
 * it feeds is tested directly instead; what a device still has to prove is that
 * the setting arrives as the boolean these cases assume.
 */
val SystemLocaleAndroidTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("theCycleFamilyFollowsThe24HourSetting") {
        assertEquals("c24", hourCycleKeyword(is24Hour = true))
        assertEquals("c12", hourCycleKeyword(is24Hour = false))
    }

    test("withoutAnApplicationContextThereIsNoKeyword") {
        val restore = LocaleContext.applicationContext
        try {
            LocaleContext.applicationContext = null
            assertNull(platformHourCycleKeyword())
        } finally {
            LocaleContext.applicationContext = restore
        }
    }

    test("theInitializerWaitsOnNothingElse") {
        assertEquals(emptyList(), LocaleContextInitializer().dependencies())
    }
}
