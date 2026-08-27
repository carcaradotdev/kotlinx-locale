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
 * Holds every shipped tailoring to ICU, which is what the other domains do.
 *
 * The corpus is built from each locale's own rule elements, so it exercises the
 * letters that locale actually tailors rather than a generic word list: the
 * Czech digraph, the Hungarian expansions, the Icelandic reset-before rules.
 */
class CollationTailoringTest {

    private val dataDir = File(
        System.getenv("COLLATION_DATA")
            ?: System.getProperty("collation.data")
            ?: "build/collation-data",
    )

    private val locales = listOf("en", "de", "es", "it", "pt", "hr", "cs", "is", "sk", "hu", "vi")

    @Test
    fun everyShippedTailoringAgreesWithIcu() {
        // The generator's inputs come from the pinned CLDR checkout, which a plain
        // `check` does not have. Point -Dcollation.data at it to run these.
        if (!dataDir.isDirectory) { println("skipped: no collation data at $dataDir"); return }
        val root = parseFractionalUca(File(dataDir, "FractionalUCA.txt"))
        val ranks = WeightRanks.of(root)
        val normalization = parseNormalizationData()

        val disagreeing = ArrayList<String>()
        for (locale in locales) {
            val rules = collationRules(File(dataDir, "collation/$locale.xml"))
            val table = TailoredTable(root, ranks, normalization)
            if (rules.isNotEmpty()) table.apply(rules)

            val words = corpus(rules)
            val mine = words.sortedWith { a, b -> compareKeys(table.sortKey(a), table.sortKey(b)) }
            val icu = Collator.getInstance(ULocale(locale)).apply { strength = Collator.TERTIARY }
            val theirs = words.sortedWith(icu)

            if (mine != theirs) {
                val at = mine.indices.first { mine[it] != theirs[it] }
                disagreeing.add("$locale at $at: mine=${mine[at]} icu=${theirs[at]}")
            } else {
                println("$locale: ${words.size} words identical to ICU (delta ${table.encodeDelta().length} chars)")
            }
        }
        assertTrue(disagreeing.isEmpty(), "locales disagreeing with ICU:\n" + disagreeing.joinToString("\n"))
    }

    @Test
    fun tailoringPlacesTheLettersItsReadersLookFor() {
        // The generator's inputs come from the pinned CLDR checkout, which a plain
        // `check` does not have. Point -Dcollation.data at it to run these.
        if (!dataDir.isDirectory) { println("skipped: no collation data at $dataDir"); return }
        val root = parseFractionalUca(File(dataDir, "FractionalUCA.txt"))
        val ranks = WeightRanks.of(root)
        val normalization = parseNormalizationData()

        fun sorted(locale: String, names: List<String>): List<String> {
            val table = TailoredTable(root, ranks, normalization)
            collationRules(File(dataDir, "collation/$locale.xml")).takeIf { it.isNotEmpty() }?.let { table.apply(it) }
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
    }

    private fun corpus(rules: String): List<String> {
        val words = LinkedHashSet<String>()
        ('a'..'z').forEach { words.add(it.toString()) }
        ('A'..'Z').forEach { words.add(it.toString()) }
        val elements = tokenizeRules(rules).filter { it.kind == "text" && it.text.isNotEmpty() }.map { it.text }
        words.addAll(elements)
        for (a in elements.take(26)) {
            for (b in elements.take(26)) words.add(a + b)
            words.add(a + "a"); words.add("a" + a); words.add(a + "z")
        }
        return words.toList()
    }

    private fun compareKeys(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until minOf(a.size, b.size)) if (a[i] != b[i]) return a[i].compareTo(b[i])
        return a.size.compareTo(b.size)
    }
}
