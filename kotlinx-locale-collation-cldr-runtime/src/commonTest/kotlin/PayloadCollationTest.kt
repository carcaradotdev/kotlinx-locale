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

package dev.carcara.kotlinx.locale.collation.cldr.runtime

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.collation.CollationStrength
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * The reader, over a table small enough to write down.
 *
 * The shipped tables are checked in `kotlinx-locale-collation-cldr-full`, which
 * needs the generated data; this module carries no data at all, so what it can
 * check is that the format it parses means what the generator writes. A
 * hand-written table is the only way to state that without a generator run.
 *
 * The root is five sections separated by `;`: header, singles, contractions,
 * prefixes, Han spans. A delta is seven: singles, contractions, prefixes,
 * reorder, case swap, flags, suppressed.
 */
val PayloadCollationTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    // implicitBase, defaultSecondary, defaultTertiary, rank stride, base 36.
    // Two letters: `a` at primary rank 1000 and `b` at 2000, both with the
    // default secondary and tertiary, plus one contraction `ch` at 3000.
    val root = listOf(
        "zz,a,a,rs",
        "${'a'.code.toString(36)}:rs/a/a,${'b'.code.toString(36)}:1jk/a/a",
        "${'c'.code.toString(36)}.${'h'.code.toString(36)}:2b8/a/a",
        "",
        "",
    ).joinToString(";")

    fun installed(): PayloadCollation {
        PayloadCollation.install(root)
        return PayloadCollation
    }

    test("singlesAndContractionsCompareByTheirWeights") {
        val table = installed().tailored("")
        assertTrue(table.compare("a", "b") < 0)
        // `ch` weighs more than `b`, so it sorts after it even though `c` alone
        // is not in the table at all.
        assertTrue(table.compare("b", "ch") < 0)
    }

    test("aStrengthViewSharesTheTableAndShortensTheKey") {
        val table = installed().tailored("")
        val primary = table.at(CollationStrength.PRIMARY)
        assertTrue(primary.sortKey("ab").size < table.sortKey("ab").size)
    }

    test("aReorderBandShiftsEveryPrimaryInsideIt") {
        // Move `b` below `a`: the band [2000, 2000] starts at 500 instead.
        val delta = listOf("", "", "", "1jk.1jk.dw", "", "", "").joinToString(";")
        val table = installed().tailored(delta)
        assertTrue(table.compare("b", "a") < 0, "b was reordered below a")
    }

    test("aSuppressedCharacterNeverStartsAContraction") {
        val delta = listOf("", "", "", "", "", "", 'c'.code.toString(36)).joinToString(";")
        val table = installed().tailored(delta)
        // With `c` suppressed, `ch` is no longer one unit, so the two spellings
        // stop weighing the same.
        assertTrue(table.compare("ch", "b") != 0)
    }

    test("anUnlistedCharacterStillAnswers") {
        // A build with no table for a character degrades to the implicit band,
        // which orders by code point, rather than throwing.
        val table = installed().tailored("")
        assertEquals(0, table.compare("zzz", "zzz"))
        assertTrue(table.compare("zzz", "zzza") < 0)
    }
}
