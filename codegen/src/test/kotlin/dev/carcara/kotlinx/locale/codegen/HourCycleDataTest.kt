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

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.test.assertEquals
import java.io.File

val HourCycleDataTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val rootDir = File(
        System.getProperty("kotlinx.locale.rootDir") ?: error("kotlinx.locale.rootDir is not set"),
    )
    val cldrDir: File = reposDir(rootDir).resolve("cldr")
    val cloned: Boolean = cldrDir.resolve("common/supplemental/supplementalData.xml").isFile
    val supplemental: SupplementalData by lazy { parseSupplemental(cldrDir) }
    val flattener: Flattener by lazy { Flattener(cldrDir, supplemental) }

    test("japanIsTheOnlyRowThatAllowsK") {
        if (!cloned) return@test
        assertEquals('H', supplemental.hourCycleFor("ja_JP").preferred)
        assertEquals(listOf("H", "K", "h"), supplemental.hourCycleFor("ja_JP").allowed)
    }

    test("everyPreferredIsHOrLowercaseH") {
        if (!cloned) return@test
        val preferred = supplemental.hourCycles.values.map { it.preferred }.toSet()
        assertEquals(setOf('H', 'h'), preferred)
    }

    test("field26CarriesThePreferredAndAllowedRow") {
        if (!cloned) return@test
        val row = flattener.resolve("en_US").encode().split(FIELD_SEPARATOR)[26].split(LIST_SEPARATOR)
        assertEquals("h", row[0])
        assertEquals(supplemental.hourCycleFor("en_US").allowed, row.drop(1))
    }
}
