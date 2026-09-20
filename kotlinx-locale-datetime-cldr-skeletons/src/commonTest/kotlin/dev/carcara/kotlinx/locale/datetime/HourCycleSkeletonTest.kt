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

package dev.carcara.kotlinx.locale.datetime

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.format
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNotEquals
import kotlinx.datetime.LocalTime

val HourCycleSkeletonTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val time = LocalTime(15, 30)

    fun locale(tag: String) = Locale.forLanguageTag(tag)

    // CLDR separates a 12-hour time from its day period with U+202F, not an ASCII space.
    val nnbsp = '\u202F'

    test("jFollowsTheOverride") {
        assertEquals("15:30", time.format("jm", locale("en-US-u-hc-h23")))
        assertEquals("3:30${nnbsp}PM", time.format("jm", locale("de-DE-u-hc-h12")))
    }

    test("jWithoutAnOverrideIsUnchanged") {
        assertEquals(
            time.format("jm", locale("en-US")),
            time.format("jm", locale("en-US-u-nu-latn")),
        )
    }

    test("cyclesThatResolveAgainstTheAllowedListReachTheSkeletonPath") {
        // ja: allowed is H K h, so c12 gives h11 (K) and c24 gives h23 (H).
        // At 15:30 the K/h numeral is the same ("3"), so this pins the h23 answer
        // and, separately, a midnight case where K writes "0" and h writes "12".
        val midnight = LocalTime(0, 30)
        assertEquals("15:30", time.format("jm", locale("ja-u-hc-c24")))
        assertEquals("午前0:30", midnight.format("jm", locale("ja-u-hc-c12")))
        assertEquals("午前0:30", midnight.format("jm", locale("ja-u-hc-h11")))
        assertNotEquals(
            midnight.format("jm", locale("ja-u-hc-c12")),
            midnight.format("jm", locale("ja-u-hc-h12")),
        )
    }

    test("cIsUnaffectedByTheOverrideThatChangesJ") {
        val plain = locale("de-DE")
        val overridden = locale("de-DE-u-hc-h12")
        assertNotEquals(time.format("jm", plain), time.format("jm", overridden))
        assertEquals(time.format("Cm", plain), time.format("Cm", overridden))
        assertEquals("15:30", time.format("Cm", overridden))
    }
}
