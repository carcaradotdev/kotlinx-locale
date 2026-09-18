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
import dev.carcara.kotlinx.locale.test.assertTrue
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

    test("multiCharacterAllowedEntriesSurviveVerbatim") {
        if (!cloned) return@test
        val allowed = supplemental.hourCycleFor("hi_IN").allowed
        assertTrue(allowed.any { it.length > 1 }, "hi_IN allowed was $allowed")
        assertEquals("hB", allowed.first())
    }

    test("koreanCrossesToATwentyFourHourShort") {
        if (!cloned) return@test
        val alternates = flattener.resolve("ko").alternateTimeFormats
        assertEquals("HH:mm", alternates[3])
    }

    test("danishDropsTheHourWidthWithTheFamily") {
        if (!cloned) return@test
        val alternates = flattener.resolve("da").alternateTimeFormats
        assertEquals("h.mm\u202Fa", alternates[3])
    }

    test("englishCrossesToATwentyFourHourShort") {
        if (!cloned) return@test
        val alternates = flattener.resolve("en").alternateTimeFormats
        assertEquals("HH:mm", alternates[3])
    }

    test("aZoneLeadingPatternContributesNoTail") {
        if (!cloned) return@test
        val resolved = flattener.resolve("zh")
        assertEquals("zzzz HH:mm:ss", resolved.timeFormats[0])
        assertEquals("ah:mm:ss", resolved.alternateTimeFormats[0])
    }

    test("theZoneTailLeavesTheWordBeforeItBehind") {
        if (!cloned) return@test
        val resolved = flattener.resolve("th")
        assertEquals(
            "h:mm:ss a zzzz",
            resolved.alternateTimeFormats[0],
            "th names the seconds in a word ending in a combining mark: ${resolved.timeFormats[0]}",
        )
    }

    test("anAlternateEqualToTheOwnPatternIsEmpty") {
        if (!cloned) return@test
        val byn = flattener.resolve("byn")
        assertEquals("h:mm:ss a zzzz", byn.timeFormats[0], "byn states a FULL of the family its MEDIUM is not")
        assertEquals("", byn.alternateTimeFormats[0])
        val repeats = flattener.localeIds.filter { id ->
            val resolved = flattener.resolve(id)
            (0..3).any { resolved.alternateTimeFormats[it] == resolved.timeFormats[it] }
        }
        assertEquals(emptyList<String>(), repeats, "locales repeating their own pattern as the alternate")
    }

    test("field27CarriesTheAlternateTimePatterns") {
        if (!cloned) return@test
        val resolved = flattener.resolve("en_US")
        val fields = resolved.encode().split(FIELD_SEPARATOR)
        assertEquals(28, fields.size)
        assertEquals(resolved.alternateTimeFormats, fields[27].split(LIST_SEPARATOR))
    }

    test("almostEveryLocaleCanAnswerAnOverride") {
        if (!cloned) return@test
        val silent = flattener.localeIds.filter { flattener.resolve(it).alternateTimeFormats.all(String::isEmpty) }
        assertEquals(0, silent.size, "locales carrying no alternate time pattern: $silent")
    }
}
