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

package dev.carcara.kotlinx.locale.codegen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [LICENSE_HEADER] to the LICENSE file at the repository root.
 *
 * Two things write that notice into source files: this constant, for everything
 * :codegen emits, and `scripts/license_header.py`, for everything a person
 * wrote. Both read from LICENSE, one at build time and one from a checkout, and
 * a thousand generated files sit where they would disagree. Editing the
 * copyright line in LICENSE and nowhere else has to be enough.
 */
class LicenseHeaderTest {

    private val rootDir = File(
        System.getProperty("kotlinx.locale.rootDir") ?: error("kotlinx.locale.rootDir is not set"),
    )

    @Test
    fun theHeaderIsTheNoticeOutOfTheLicenseFile() {
        val lines = rootDir.resolve("LICENSE").readText().split("\n")

        val appendix = lines.indexOfFirst { "APPENDIX:" in it }
        assertTrue(appendix >= 0, "LICENSE has no APPENDIX section to take the notice from")

        val notice = lines.drop(appendix)
            .dropWhile { !it.trim().startsWith("Copyright ") }
            .map { it.removePrefix("   ") }
            .dropLastWhile(String::isBlank)
        assertTrue(
            notice.any { "Licensed under the Apache License" in it },
            "the notice taken from LICENSE does not read like one",
        )

        assertEquals(
            notice.joinToString("\n", prefix = "/*\n", postfix = "\n */\n\n") { " * $it".trimEnd() },
            LICENSE_HEADER,
            "LICENSE_HEADER has drifted from LICENSE",
        )
    }
}
