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

package dev.carcara.kotlinx.locale.icu

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import com.ibm.icu.text.Collator
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.collation.CollationStrength
import dev.carcara.kotlinx.locale.collation.cldr.collationComparator
import dev.carcara.kotlinx.locale.country.cldr.CldrCountry
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * The collation order, held to ICU4J across every locale this library ships.
 *
 * The shipped `commonTest` cases prove the tables unpack and sort the same on
 * twenty-four targets. This proves the order is right, which a hand-written case
 * cannot: the interesting failures are in locales nobody on this project reads,
 * and the only oracle for those is the implementation everyone else uses.
 *
 * Whole lists rather than pairs. A comparator is wrong in a way a pair rarely
 * catches: two letters can be in the right relative order while the block they
 * belong to sits in the wrong place, and only sorting a mixed list shows it.
 */
/**
 * The CLDR collation directives this build reads and ignores.
 *
 * Named here rather than derived from the CLDR files, because this module has no
 * CLDR checkout: it runs in CI, where `codegen/repos` does not exist. ICU's own
 * rule string carries the directives, which is the same information.
 */
private val UNREAD_RULES = listOf(
    "[alternate ",
    "[caseLevel ",
    "[caseFirst ",
    "[first ",
    "[last ",
    "[maxVariable ",
)

/** Which unread directive a locale's rules use, or null when it uses none. */
private fun unreadRuleIn(rules: String): String? = UNREAD_RULES
    .firstOrNull { it in rules }
    ?.trim()
    ?.removePrefix("[")

val CollationConformanceTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val tags = CldrCountry.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    /**
     * A word list built from the letters the locale itself cares about.
     *
     * The Latin alphabet is in it whatever the locale, because reordering is
     * about where a script sits relative to another one and a list in a single
     * script cannot show that. The locale's own letters come from ICU's rule
     * string, which is the tailoring written out.
     */
    fun corpus(rules: String): List<String> {
        val words = LinkedHashSet<String>()
        ('a'..'z').forEach { words.add(it.toString()) }
        ('A'..'Z').forEach { words.add(it.toString()) }
        // Letters only. Splitting the rule text on its operators looked simpler
        // and is not: it yields fragments of `[before 1]` and the script names
        // out of `[reorder Deva]`, and a disagreement about how to sort the word
        // "before" says nothing about the collation order.
        val own = LinkedHashSet<String>()
        var index = 0
        while (index < rules.length) {
            val point = rules.codePointAt(index)
            index += Character.charCount(point)
            if (Character.isLetter(point)) own.add(String(Character.toChars(point)))
        }
        words.addAll(own)
        // Pairs, because a contraction is two letters that weigh as one and a
        // single-letter list cannot show one.
        for (first in own.take(24)) {
            for (second in own.take(24)) words.add(first + second)
            words.add(first + "a")
            words.add("a" + first)
        }
        return words.toList()
    }

    test("ICU4J on the classpath is the pinned release") {
        IcuHarness.assertIcuMatchesThePin()
    }

    test("every locale that agrees is counted, not assumed") {
        // The guard against a comparison that passes by comparing nothing: if the
        // corpus builder returned an empty list every locale would agree.
        val en = Collator.getInstance(IcuHarness.uLocale("en")) as com.ibm.icu.text.RuleBasedCollator
        assertTrue(corpus(en.rules).size > 50, "the corpus for English collapsed to ${corpus(en.rules).size} words")
    }

    test("the comparison set is the whole shipped catalogue") {
        assertTrue(
            tags.size > 500,
            "only ${tags.size} locales are comparable against ICU, which suggests the " +
                "availability filter is wrong rather than that ICU shrank",
        )
    }

    test("collation order agrees with ICU") {
        val comparison = DomainComparison("collation-order")
        for (tag in tags) {
            val icu = Collator.getInstance(IcuHarness.uLocale(tag)).apply { strength = Collator.TERTIARY }
            val ours = collationComparator(IcuHarness.locale(tag))
            val rules = (icu as? com.ibm.icu.text.RuleBasedCollator)?.rules.orEmpty()
            val words = corpus(rules)
            val mine = words.sortedWith(ours)
            val theirs = words.sortedWith(icu)
            // One case per locale: the whole ordering, joined. A per-pair case
            // would put thousands of near-identical rows in the ledger and say
            // no more than the first disagreement in the list does.
            val at = mine.indices.firstOrNull { mine[it] != theirs[it] }
            comparison.compare(
                tag,
                "order",
                if (at == null) "agrees" else "${mine[at]} at $at",
                if (at == null) "agrees" else "${theirs[at]} at $at",
                note = {
                    unreadRuleIn(rules)?.let { "uses $it, which this build does not read; see docs/boundaries.md" }
                        ?: "the tailoring is applied and one letter lands elsewhere than ICU puts it; not yet diagnosed"
                },
            ) {
                if (unreadRuleIn(rules) != null) Divergence.NOT_IMPLEMENTED else null
            }
        }
        comparison.settle(minimumCompared = 500)
    }

    test("primary strength ignores accents and case the way ICU does") {
        val comparison = DomainComparison("collation-strength")
        val pairs = listOf(
            "resume" to "résumé",
            "a" to "A",
            "Ångström" to "angstrom",
            "naive" to "naïve",
        )
        for (tag in tags) {
            val ours = collationComparator(IcuHarness.locale(tag), CollationStrength.PRIMARY)
            val icu = Collator.getInstance(IcuHarness.uLocale(tag)).apply { strength = Collator.PRIMARY }
            for ((left, right) in pairs) {
                comparison.compare(
                    tag,
                    "$left~$right",
                    ours.compare(left, right).coerceIn(-1, 1).toString(),
                    icu.compare(left, right).coerceIn(-1, 1).toString(),
                    note = { "primary-level equality differs from ICU for this pair; not yet diagnosed" },
                ) { null }
            }
        }
        comparison.settle(minimumCompared = 2_000)
    }
}
