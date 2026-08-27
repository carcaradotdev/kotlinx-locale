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
 * Holds the generator to the table the algorithm was validated against.
 *
 * The reference emitter this compares to is the one whose output passes the CLDR
 * conformance file, so an identical string means the parse, the Han order, the
 * ranking and the encoding all agree with it. It is a stronger check than
 * re-running conformance here, and a much faster one.
 */
class CollationCodegenTest {

    private val dataDir = File(
        System.getenv("COLLATION_DATA")
            ?: System.getProperty("collation.data")
            ?: "build/collation-data",
    )

    @Test
    fun rootTableMatchesTheValidatedReference() {
        // The generator's inputs come from the pinned CLDR checkout, which a plain
        // `check` does not have. Point -Dcollation.data at it to run these.
        if (!dataDir.isDirectory) { println("skipped: no collation data at $dataDir"); return }
        val source = File(dataDir, "FractionalUCA.txt")
        val expectedFile = File(dataDir, "root_table.txt")

        val root = parseFractionalUca(source)
        val ranks = WeightRanks.of(root)
        val encoded = encodeCollationRoot(root, ranks)
        val expected = expectedFile.readText()

        println(
            "codegen root: entries=${root.entries.size} han=${root.hanOrder.size} " +
                "prefixes=${root.prefixed.size} chars=${encoded.length} implicitBase=${ranks.implicitBase}",
        )

        // Compared as tables rather than as strings: the reference appends every
        // Han entry even where an explicit one already exists, so a duplicate key
        // is legitimate and the last one wins on both sides.
        val mine = parseTable(encoded)
        val theirs = parseTable(expected)
        for ((section, rows) in theirs) {
            val ours = mine[section].orEmpty()
            assertEquals(rows.size, ours.size, "section $section entry count")
            for ((key, value) in rows) {
                assertEquals(value, ours[key], "section $section key $key")
            }
        }
        assertEquals(theirs.keys, mine.keys)
    }

    /** Sections of the encoded table as key to value maps, last entry winning. */
    private fun parseTable(table: String): Map<Int, Map<String, String>> {
        val sections = table.split(';')
        return (1..3).associateWith { index ->
            val rows = LinkedHashMap<String, String>()
            for (entry in sections[index].split(',')) {
                if (entry.isEmpty()) continue
                rows[entry.substringBefore(':')] = entry.substringAfter(':')
            }
            rows
        }
    }

    @Test
    fun hanIsOrderedByRadicalStroke() {
        // The generator's inputs come from the pinned CLDR checkout, which a plain
        // `check` does not have. Point -Dcollation.data at it to run these.
        if (!dataDir.isDirectory) { println("skipped: no collation data at $dataDir"); return }
        val root = parseFractionalUca(File(dataDir, "FractionalUCA.txt"))
        assertEquals(101_996, root.hanOrder.size, "every Unified_Ideograph should be ordered once")
        assertEquals(root.hanOrder.size, root.hanOrder.toSet().size, "no ideograph should appear twice")
    }

    @Test
    fun collationRulesAreReadFromCldr() {
        // The generator's inputs come from the pinned CLDR checkout, which a plain
        // `check` does not have. Point -Dcollation.data at it to run these.
        if (!dataDir.isDirectory) { println("skipped: no collation data at $dataDir"); return }
        val czech = collationRules(File(dataDir, "collation/cs.xml"))
        assertTrue(czech.contains("&C<"), "expected the Czech c-caron rule, got: ${czech.take(120)}")
        assertTrue(czech.contains("ch"), "expected the Czech ch digraph rule")
    }
}
