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

package dev.carcara.kotlinx.locale.platform

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * The capability contract, asserted on every target.
 *
 * Both branches of every check assert something. A test that returns early on the
 * targets it does not like still passes there, which makes it look like coverage
 * it is not: the four targets with no locale data are exactly the ones where a
 * silent no-op would hide a regression.
 */
val PlatformLocaleDataTest by matrixSuite {

    test("availabilityAndEnumerationAgree") {
        val tags = PlatformLocaleData.availableLocaleTags()
        if (PlatformLocaleData.isAvailable) {
            // JS and Wasm/JS are available and enumerate nothing, which is allowed
            // and is why this is not an assertion that the set is non-empty.
            assertTrue(tags.isEmpty() || tags.size > 10, "an enumerable platform should list more than a handful: $tags")
        } else {
            // Unavailable means nothing to enumerate. If this ever starts failing,
            // a target grew locale data and its actuals need writing.
            assertEquals(emptySet(), tags, "an unavailable platform must not claim locales")
        }
    }

    test("everyEnumeratedTagParses") {
        val tags = PlatformLocaleData.availableLocaleTags()
        // Foundation returns identifiers such as pt_BR rather than pt-BR, so this
        // is also the check that the lenient parser covers what the hosts emit.
        val unparseable = tags.filter { Locale.forLanguageTagOrNull(it) == null }
        // The platforms carry a few identifiers that are not locales at all, such
        // as the root or POSIX entries, so a handful is expected; a pile is a sign
        // the tags are not what we think they are.
        assertTrue(
            unparseable.size <= tags.size / 20,
            "${unparseable.size} of ${tags.size} platform tags did not parse: ${unparseable.take(10)}",
        )
    }

    test("enumerationIsStableAcrossCalls") {
        // The domain sources cache supportedLocales lazily, which is only sound if
        // the platform answers the same way twice.
        assertEquals(PlatformLocaleData.availableLocaleTags(), PlatformLocaleData.availableLocaleTags())
    }
}
