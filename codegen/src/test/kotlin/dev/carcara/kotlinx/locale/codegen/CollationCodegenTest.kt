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
 * What the generator reads out of `FractionalUCA.txt`, checked at the source.
 *
 * Reads the pinned CLDR clone the way every other generator test does, and skips
 * where there is none, which is every checkout that has not run
 * `:codegen:cloneLocaleRepos`, CI included. The shipped tables are checked
 * elsewhere and everywhere: `CollationOrderTest` runs on all twenty-four targets
 * and `CollationConformanceTest` compares 905 locales against ICU4J. This is the
 * second opinion on the reading rather than the only opinion on the answer.
 *
 * It used to point at a `build/collation-data` directory that nothing created,
 * so all of it skipped and reported green.
 */
class CollationCodegenTest {

    private val rootDir = File(
        System.getProperty("kotlinx.locale.rootDir") ?: error("kotlinx.locale.rootDir is not set"),
    )

    private val cldrDir = reposDir(rootDir).resolve("cldr")

    private val uca = cldrDir.resolve("common/uca/FractionalUCA.txt")

    private fun cloned(): Boolean = uca.isFile

    @Test
    fun hanIsOrderedByRadicalStroke() {
        if (!cloned()) return
        val root = parseFractionalUca(uca)
        // The radical-stroke order is what FractionalUCA's `[radical ...]` lines
        // carry, and it is not code point order: U+4E00 is the first ideograph
        // by both, and the block does not stay in step after that.
        assertEquals(101_996, root.hanOrder.size, "ideographs placed by the radical lines")
        assertEquals(0x4E00, root.hanOrder.first(), "the radical order opens on U+4E00")
        assertTrue(
            root.hanOrder.zipWithNext().any { (a, b) -> b < a },
            "radical-stroke order would be code point order if it never stepped backwards",
        )
    }

    @Test
    fun everyIdeographIsCoveredByASpanAtItsOwnRank() {
        if (!cloned()) return
        val root = parseFractionalUca(uca)
        val ranks = WeightRanks.of(root)
        val spans = hanSpans(root, ranks)
        var covered = 0
        for (span in spans) {
            for (offset in 0 until span.length) {
                val codePoint = span.startCodePoint + offset
                val element = root.entries.getValue(listOf(codePoint)).single()
                assertEquals(
                    ranks.primary.getValue(element.primary),
                    span.firstRank + offset * WeightRanks.BASE_GAP,
                    "rank of U+${codePoint.toString(16).uppercase()}",
                )
                covered++
            }
        }
        // The spans are what the artifact carries in place of the entries, so a
        // span set that dropped an ideograph would sort it into the implicit
        // band, above every letter, with nothing else failing.
        assertEquals(root.hanOrder.size, covered, "every ideograph is described by a span")
        assertTrue(spans.size < root.hanOrder.size / 4, "spans should be far fewer than entries")
    }

    @Test
    fun theReorderingGroupsCoverEveryScriptALocaleNames() {
        if (!cloned()) return
        val root = parseFractionalUca(uca)
        val named = File(cldrDir, "common/collation").listFiles().orEmpty()
            .filter { it.name.endsWith(".xml") }
            .flatMap { file ->
                tokenizeRules(collationRules(file))
                    .filter { it.kind == "directive" && it.text.startsWith("reorder") }
                    .flatMap { it.text.removePrefix("reorder").trim().split(' ') }
            }
            .filter(String::isNotEmpty)
            .toSet()
        assertTrue(named.isNotEmpty(), "CLDR should name some scripts to reorder")
        // A script with no group is a reorder directive that silently does
        // nothing, which is how Russian sorted in root order for a release.
        val ungrouped = named.filterNot { it in root.groupOfScript }
        assertEquals(emptyList(), ungrouped, "every reordered script maps to a group")
    }

    @Test
    fun collationRulesAreReadFromCldr() {
        if (!cloned()) return
        val collationDir = cldrDir.resolve("common/collation")
        // Czech is the smallest rule set that exercises the parts that matter: a
        // reset, a primary difference and a digraph contraction.
        // Whitespace stripped rather than spaces: CLDR indents its rules with
        // tabs, and the first version of this check looked only for spaces and
        // passed on an empty read.
        val czech = collationRules(collationDir.resolve("cs.xml")).filterNot(Char::isWhitespace)
        // Decomposed, and that is the point of asserting it. CLDR writes some
        // tailorings composed and some not: Czech spells c-caron as `c` plus
        // U+030C COMBINING CARON while Croatian spells the same letter as one
        // code point. The generator keys its entries on the decomposed form for
        // exactly this reason, and a reader that assumed the composed spelling
        // would silently lose every Czech rule.
        assertTrue("&C<c\u030C" in czech, "the Czech caron rule is in the file, decomposed")
        assertTrue("&C<\u010D" !in czech, "CLDR does not write the Czech caron composed")
        assertTrue("&H<ch" in czech, "the Czech digraph rule is in the file")

        // An import resolves to the file it names rather than to nothing.
        assertTrue(
            importedRules(collationDir, "de-u-co-phonebk").orEmpty().isNotEmpty(),
            "the German phone book ordering resolves",
        )
        // The private orderings do exist in the checkout, under root, which is
        // why Japanese and Chinese get theirs.
        assertTrue(
            importedRules(collationDir, "und-u-co-private-unihan").orEmpty().isNotEmpty(),
            "the private unihan ordering resolves from root",
        )
        // Something genuinely absent answers null rather than throwing, so one
        // missing import cannot stop the whole generation.
        assertEquals(null, importedRules(collationDir, "zz-u-co-standard"))
        assertEquals(null, importedRules(collationDir, "de-u-co-nosuchtype"))
    }
}
