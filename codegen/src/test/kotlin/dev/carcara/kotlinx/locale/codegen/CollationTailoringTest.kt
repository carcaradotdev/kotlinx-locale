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

import com.ibm.icu.text.Collator
import com.ibm.icu.util.ULocale
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tailoring generator, held to ICU at the point the tables are made.
 *
 * `CollationConformanceTest` in `:conformance-icu` compares the shipped artifact
 * across 905 locales and is the broader check. This one runs against the
 * generator directly, so a rule the parser drops is caught here with the rule in
 * hand rather than as a locale that sorts oddly three steps downstream.
 *
 * Skipped where the CLDR clone is absent, which is CI.
 */
class CollationTailoringTest {

    private val rootDir = File(
        System.getProperty("kotlinx.locale.rootDir") ?: error("kotlinx.locale.rootDir is not set"),
    )

    private val cldrDir = reposDir(rootDir).resolve("cldr")
    private val uca = cldrDir.resolve("common/uca/FractionalUCA.txt")
    private val collationDir = cldrDir.resolve("common/collation")

    private fun cloned(): Boolean = uca.isFile

    private fun tableFor(locale: String, root: CollationRoot, ranks: WeightRanks, norm: NormalizationData) =
        tailoringFor(root, ranks, norm, collationRules(collationDir.resolve("$locale.xml"))) { id ->
            importedRules(collationDir, id)
        }

    @Test
    fun everyTailoringCldrShipsCanBeBuilt() {
        if (!cloned()) return
        val root = parseFractionalUca(uca)
        val ranks = WeightRanks.of(root)
        val norm = parseNormalizationData()

        val broken = ArrayList<String>()
        var built = 0
        for (file in collationDir.listFiles().orEmpty().filter { it.name.endsWith(".xml") }.sortedBy(File::getName)) {
            val rules = collationRules(file)
            if (rules.isEmpty()) continue
            try {
                tailoringFor(root, ranks, norm, rules) { id -> importedRules(collationDir, id) }
                built++
            } catch (e: Exception) {
                broken.add("${file.name}: ${e.message}")
            }
        }
        // A tailoring that cannot be built is a locale that silently sorts in
        // root order, which is the failure this whole file exists to prevent.
        assertEquals(emptyList(), broken, "tailorings that could not be built")
        assertTrue(built > 100, "only $built tailorings were built, which suggests the reader stopped early")
    }

    @Test
    fun tailoringPlacesTheLettersItsReadersLookFor() {
        if (!cloned()) return
        val root = parseFractionalUca(uca)
        val ranks = WeightRanks.of(root)
        val norm = parseNormalizationData()

        fun sorted(locale: String, names: List<String>): List<String> {
            val table = tableFor(locale, root, ranks, norm)
            return names.sortedWith { a, b -> compareKeys(table.sortKey(a), table.sortKey(b)) }
        }

        // Thorn ends the Icelandic alphabet, which no diacritic fold can do.
        assertEquals(
            listOf("Albanía", "Írland", "Ísland", "Ítalía", "Japan", "Þýskaland"),
            sorted("is", listOf("Þýskaland", "Ísland", "Japan", "Albanía", "Ítalía", "Írland")),
        )
        // Czech sorts č after every c, and the ch digraph after h.
        assertEquals(
            listOf("Cyprus", "Česko", "Dánsko", "Chorvatsko"),
            sorted("cs", listOf("Chorvatsko", "Česko", "Cyprus", "Dánsko")),
        )
        // Hungarian reads cs and zs as single letters.
        assertEquals(
            listOf("Ciprus", "Csád", "Zambia", "Zsombó"),
            sorted("hu", listOf("Zsombó", "Csád", "Ciprus", "Zambia")),
        )
        // Russian lifts the whole Cyrillic block above Latin, which is a
        // `[reorder]` directive rather than any letter rule.
        assertEquals(
            listOf("Ель", "Elm"),
            sorted("ru", listOf("Elm", "Ель")),
        )
    }

    @Test
    fun everyTailoredLocaleAgreesWithIcu() {
        if (!cloned()) return
        val root = parseFractionalUca(uca)
        val ranks = WeightRanks.of(root)
        val norm = parseNormalizationData()

        // The locales whose rules this build reads in full. The rest are
        // recorded in conformance/ledger/collation-order.tsv with the directive
        // that explains them, and comparing them here would duplicate that.
        // Not `th`: Thai asks for `[alternate shifted]`, which this build reads
        // and does not apply, and the ledger records it.
        val locales = listOf("en", "de", "es", "it", "pt", "hr", "cs", "is", "sk", "hu", "vi", "ru", "el", "he")
        val disagreeing = ArrayList<String>()
        for (locale in locales) {
            val table = tableFor(locale, root, ranks, norm)
            val words = corpus(collationRules(collationDir.resolve("$locale.xml")))
            val mine = words.sortedWith { a, b -> compareKeys(table.sortKey(a), table.sortKey(b)) }
            val icu = Collator.getInstance(ULocale(locale)).apply { strength = Collator.TERTIARY }
            val theirs = words.sortedWith(icu)
            if (mine != theirs) {
                val at = mine.indices.first { mine[it] != theirs[it] }
                disagreeing.add("$locale at $at: mine=${mine[at]} icu=${theirs[at]}")
            }
        }
        assertEquals(emptyList(), disagreeing, "locales disagreeing with ICU")
    }

    private fun corpus(rules: String): List<String> {
        val words = LinkedHashSet<String>()
        ('a'..'z').forEach { words.add(it.toString()) }
        ('A'..'Z').forEach { words.add(it.toString()) }
        val elements = tokenizeRules(rules).filter { it.kind == "text" && it.text.isNotEmpty() }.map { it.text }
        words.addAll(elements)
        for (a in elements.take(26)) {
            for (b in elements.take(26)) words.add(a + b)
            words.add(a + "a")
            words.add("a" + a)
            words.add(a + "z")
        }
        return words.toList()
    }

    private fun compareKeys(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until minOf(a.size, b.size)) if (a[i] != b[i]) return a[i].compareTo(b[i])
        return a.size.compareTo(b.size)
    }
}
