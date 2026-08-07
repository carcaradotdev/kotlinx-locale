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

import com.ibm.icu.util.ULocale
import com.ibm.icu.util.VersionInfo
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue

/** The releases this build is pinned to. Kept next to the ledger they stamp. */
const val ICU_PIN: String = "release-78.3"
const val CLDR_PIN: String = "release-48-2"

/** ICU4J's major version, which must be the one `Repos.kt` clones. */
private const val ICU_MAJOR: Int = 78

/**
 * Everything a domain comparison needs, set up once.
 *
 * ## Why the defaults are pinned here and not per test
 *
 * `ULocale.setDefault` and `TimeZone.setDefault` are process-global mutable
 * state. ICU resolves a locale it does not carry against the *default* one, so
 * on an unpinned JVM every such locale silently answers in whatever the build
 * machine was set to, and the same test passes on one laptop and fails on
 * another. `:codegen` already hit this and pins the same two values.
 *
 * They are set in an initializer rather than before each comparison because the
 * comparisons run in parallel across locales, and two threads racing a global
 * setter is a worse bug than the one being prevented.
 */
object IcuHarness {

    init {
        ULocale.setDefault(ULocale.ROOT)
        com.ibm.icu.util.TimeZone.setDefault(com.ibm.icu.util.TimeZone.GMT_ZONE)
    }

    /**
     * The ICU on the classpath is the ICU the goldens were cut from.
     *
     * `:codegen` has `crossCheckIcuVersion`, but it only runs when someone
     * regenerates, which CI never does. This is the same check somewhere it
     * actually runs, so bumping `libs.versions.toml` without bumping
     * `Repos.kt` fails in the module that cares.
     */
    fun assertIcuMatchesThePin() {
        assertEquals(
            ICU_MAJOR,
            VersionInfo.ICU_VERSION.major,
            "ICU4J on the test classpath is ${VersionInfo.ICU_VERSION}, and the CLDR data is " +
                "pinned against $ICU_PIN. Bump icu4j in libs.versions.toml and ICU_REPO.tag in " +
                "Repos.kt together, then regenerate the ledger.",
        )
    }

    /**
     * The locales ICU can actually answer for, as `ULocale`s keyed by this
     * library's tag.
     *
     * ICU carries fewer locales than CLDR publishes. For one it does not carry,
     * `ULocale` silently resolves to an ancestor bundle and answers from there,
     * which looks like a disagreement and is not one. Comparing all 1121
     * without this filter makes roughly a fifth of every domain's ledger noise,
     * and a ledger that is mostly noise is a ledger nobody reads a second time.
     */
    val available: Set<String> by lazy {
        ULocale.getAvailableLocales().mapTo(HashSet()) { it.toLanguageTag() }
    }

    /** True when ICU has a bundle of its own for [tag] rather than an ancestor's. */
    fun icuCarries(tag: String): Boolean = tag in available

    fun uLocale(tag: String): ULocale = ULocale.forLanguageTag(tag)

    fun locale(tag: String): Locale = Locale.forLanguageTag(tag)
}

/**
 * Normalizes the no-break space variants that ICU and CLDR point releases
 * disagree on: U+00A0 NO-BREAK SPACE and U+202F NARROW NO-BREAK SPACE.
 *
 * The same normalization the committed goldens use. A difference that is only
 * which non-breaking space a release chose is not a difference anybody sees.
 */
fun String.normalizedSpaces(): String = replace(' ', ' ').replace(' ', ' ')

/**
 * Collects one domain's comparisons and settles them against the ledger.
 *
 * A comparison reports through [agree] or [differ]; nothing throws until
 * [settle], so one run produces the whole picture rather than the first
 * disagreement. That matters here more than in an ordinary test: at eleven
 * hundred locales the interesting output is which locales moved, and stopping
 * at the first one hides the shape of it.
 */
class DomainComparison(private val domain: String) {

    private val found = LinkedHashMap<String, LedgerRow>()
    private val derived = LinkedHashMap<Divergence, Long>()
    private var compared = 0L

    // Distinct locales, not cases. The two answer different questions and the
    // locale count is the one a reader means by "how much is checked".
    private val localesCompared = HashSet<String>()
    private var skipped = 0L

    /** Records a case that was not compared, and why, so the totals stay honest. */
    fun skip(divergence: Divergence) {
        skipped++
        derived[divergence] = (derived[divergence] ?: 0) + 1
    }

    /**
     * Compares one case.
     *
     * [classify] is called only when the two disagree, so a comparison pays
     * nothing for the classifier on the overwhelming majority of cases that
     * agree.
     */
    fun compare(locale: String, case: String, ours: String, icu: String, classify: () -> Divergence?) {
        compared++
        localesCompared.add(locale)
        if (ours.normalizedSpaces() == icu.normalizedSpaces()) return
        val kind = classify()
        if (kind != null && kind != Divergence.DEFECT && kind != Divergence.DELIBERATE) {
            derived[kind] = (derived[kind] ?: 0) + 1
            return
        }
        val row = LedgerRow(domain, locale, case, ours, icu, kind ?: Divergence.DEFECT, "")
        found[row.key()] = row
    }

    /**
     * Fails unless the run matches the ledger exactly, or rewrites it when
     * `updateLedger` asked for that.
     *
     * [minimumCompared] is the guard against the failure that looks like
     * success. A comparison whose input list came back empty compares nothing,
     * disagrees with nothing, and reports green.
     */
    fun settle(minimumCompared: Long) {
        // The digest generator's run shares this source set; stand aside for it.
        if (Ledger.writingDigests) return

        assertTrue(
            compared >= minimumCompared,
            "$domain compared $compared cases, fewer than the $minimumCompared expected. " +
                "A comparison that shrank to nothing reports the same green as one that passed.",
        )

        val ledger = Ledger.open()
        val counts = derived.entries.associate { (kind, n) -> "$domain\t${kind.name}" to n }

        if (Ledger.writing) {
            // Notes are the one thing a regeneration cannot invent, so carry
            // over whatever the reviewed file already said for a row that is
            // still there.
            val existing = ledger.read(domain)
            ledger.write(domain, found.values.map { row -> row.copy(note = existing[row.key()]?.note ?: row.note) })
            ledger.writeCounts(ledger.readCounts() + counts)
            ledger.recordCoverage(domain, localesCompared.size.toLong(), compared)
            return
        }

        val recorded = ledger.read(domain)
        val unexpected = found.keys - recorded.keys
        val stale = recorded.keys - found.keys

        val problems = buildList {
            if (unexpected.isNotEmpty()) {
                add(
                    "${unexpected.size} new divergences from ICU that the ledger does not record:\n" +
                        unexpected.take(20).joinToString("\n") { key ->
                            val row = found.getValue(key)
                            "    ${row.locale} ${row.case}: ours=<${row.ours}> icu=<${row.icu}>"
                        } +
                        if (unexpected.size > 20) "\n    ... and ${unexpected.size - 20} more" else "",
                )
            }
            if (stale.isNotEmpty()) {
                add(
                    "${stale.size} ledger rows no longer reproduce and should be deleted:\n" +
                        stale.take(20).joinToString("\n") { "    $it" },
                )
            }
            val unexplained = recorded.values.filter { it.note == Ledger.UNEXPLAINED }
            if (unexplained.isNotEmpty()) {
                add(
                    "${unexplained.size} ledger rows are still carrying the marker updateLedger wrote. " +
                        "Each one is either an ICU defect or a deliberate divergence, and which it is " +
                        "cannot be derived; say so in the note column:\n" +
                        unexplained.take(20).joinToString("\n") { "    ${it.locale} ${it.case}" },
                )
            }
            val recordedCounts = ledger.readCounts()
            for ((key, n) in counts) {
                val was = recordedCounts[key]
                if (was != null && was != n) {
                    add(
                        "$key moved from $was to $n. That number is pinned because the classifier " +
                            "excusing more cases than it used to is how a real bug stops being reported.",
                    )
                }
            }
        }

        assertTrue(
            problems.isEmpty(),
            "$domain: compared $compared cases, $skipped skipped.\n" +
                problems.joinToString("\n\n") +
                "\n\nIf these are expected, run ./gradlew :conformance-icu:updateLedger and review the diff.",
        )
    }
}
