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
import dev.carcara.kotlinx.locale.test.assertNotEquals

val LocaleDefaultsTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("theDefaultWinsOverThePlatform") {
        val restore = LocaleDefaults.locale
        try {
            LocaleDefaults.locale = Locale.forLanguageTag("de-DE-u-hc-h12")
            assertEquals("de-DE-u-hc-h12", Locale.current.toLanguageTag())
        } finally {
            LocaleDefaults.locale = restore
        }
    }

    test("clearingTheDefaultGoesBackToThePlatform") {
        val restore = LocaleDefaults.locale
        val platformLocale = Locale.current
        try {
            val override = Locale.forLanguageTag("de-DE-u-hc-h12")
            assertNotEquals(platformLocale, override)
            LocaleDefaults.locale = override
            assertEquals(override, Locale.current)
            LocaleDefaults.locale = null
            assertEquals(platformLocale, Locale.current)
        } finally {
            LocaleDefaults.locale = restore
        }
    }
}
