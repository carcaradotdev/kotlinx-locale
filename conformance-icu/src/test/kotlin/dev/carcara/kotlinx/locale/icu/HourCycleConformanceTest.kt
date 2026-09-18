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
import com.ibm.icu.text.DateFormat
import com.ibm.icu.text.DateTimePatternGenerator
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.HourCycle
import dev.carcara.kotlinx.locale.datetime.cldr.CldrDateTime
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.CldrDateTimeSkeletons
import dev.carcara.kotlinx.locale.datetime.withHourCycle
import dev.carcara.kotlinx.locale.test.assertTrue
import kotlinx.datetime.LocalTime

/**
 * The `-u-hc-` override, on both paths that honour it, against ICU.
 *
 * Two things are being asked, and they are separate because the code paths are.
 * A standard time pattern under an override comes from the opposite hour
 * family's patterns the generator ships, so this compares what the whole
 * inheritance chain plus that derivation produced. A skeleton under an override
 * goes through the matcher, where the cycle only decides which of `h H K k` the
 * hour field is written with. The skeleton half compares patterns rather than
 * rendered output, for the reason the plain skeleton comparison already gives:
 * the matcher's job is picking a pattern, and rendering would let a wrong hour
 * letter pass wherever the sample time printed the same under both.
 *
 * Only the four concrete cycles are compared. `c12` and `c24` are the standard's
 * Technical Preview values and ICU's support for them moves between releases, so
 * a comparison over them would report on ICU rather than on this library. Their
 * resolution against the locale's `allowed` list is unit-tested instead.
 *
 * Nothing here classifies a disagreement into a counted kind. Every other domain
 * in this module pins the derivable kinds by number, because writing out several
 * thousand bundle fallbacks would be a file nobody reads twice. This one is small
 * enough to enumerate, so it is enumerated: all 294 waivers are rows in
 * `conformance/ledger/hour-cycle-patterns.tsv` and
 * `conformance/ledger/hour-cycle-skeletons.tsv`, each naming its category and its
 * reason, and a number a reader cannot check against a list is exactly where a
 * real defect would sit unnoticed.
 *
 * CLDR's own `common/testData/datetime/datetime.json` carries 48 cases with an
 * `hourCycle` field, and they are not the oracle here. They cover `H12` and
 * `H23` only, over four locales, three of which ask for a non-Gregorian
 * calendar; and for `ja-JP` under `H12` the expected value is written with `K`,
 * so the case asserts the locale's twelve-hour pattern as declared rather than
 * the 1-12 cycle `h12` names. ICU maps that `K` to `h`, and so does this
 * library.
 */
val HourCycleConformanceTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val patternTags = CldrDateTime.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    val skeletonTags = CldrDateTimeSkeletons.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    test("the hour cycle comparison set is the whole shipped catalogue") {
        assertTrue(
            patternTags.size > 500 && skeletonTags.size > 500,
            "only ${patternTags.size} locales carry time patterns and ${skeletonTags.size} carry " +
                "skeletons and are comparable against ICU",
        )
    }

    test("the reference instants separate h11 from h12 and h23 from h24") {
        var twelveHour = 0
        var twentyFourHour = 0
        for (tag in patternTags) {
            val plain = IcuHarness.locale(tag)
            for (instant in REFERENCE_INSTANTS) {
                fun render(cycle: HourCycle): String? =
                    CldrDateTime.formatTimeOrNull(instant.time, FormatStyle.SHORT, plain.withHourCycle(cycle))
                if (render(HourCycle.H11) != render(HourCycle.H12)) twelveHour++
                if (render(HourCycle.H23) != render(HourCycle.H24)) twentyFourHour++
            }
        }
        assertTrue(
            twelveHour >= MINIMUM_MOVED && twentyFourHour >= MINIMUM_MOVED,
            "the instants this domain renders at tell h11 from h12 for only $twelveHour cases and h23 " +
                "from h24 for only $twentyFourHour, so the comparison below compares two of its four " +
                "cycles as the same string and a dropped K or k swap would pass",
        )
    }

    test("a standard time pattern under an hc override agrees with ICU") {
        val comparison = DomainComparison("hour-cycle-patterns")
        val moved = OverrideEffect()
        for (tag in patternTags) {
            val plain = IcuHarness.locale(tag)
            for ((style, icuStyle) in TIME_STYLES) {
                for (instant in REFERENCE_INSTANTS) {
                    val unoverridden = CldrDateTime.formatTimeOrNull(instant.time, style, plain)
                    for (cycle in CONCRETE_CYCLES) {
                        val locale = plain.withHourCycle(cycle)
                        val ours = CldrDateTime.formatTimeOrNull(instant.time, style, locale) ?: continue
                        moved.record(cycle, ours != unoverridden)
                        val format =
                            DateFormat.getTimeInstance(gregorianUtc(), icuStyle, IcuHarness.uLocale(locale.toLanguageTag()))
                        format.timeZone = ICU_UTC
                        comparison.compare(
                            tag,
                            "time/$style/${cycle.name}/${instant.label}",
                            ours,
                            format.format(instant.date),
                        ) { null }
                    }
                }
            }
        }
        moved.assertEveryCycleMoved("standard time patterns")
        comparison.settle(minimumCompared = patternTags.size * 16L)
    }

    test("a standard time pattern under an hc override writes the cycle's own hour") {
        val midnight = REFERENCE_INSTANTS.last()
        val wrong = ArrayList<String>()
        var checked = 0
        for (tag in patternTags) {
            val plain = IcuHarness.locale(tag)
            for (cycle in CONCRETE_CYCLES) {
                val rendered =
                    CldrDateTime.formatTimeOrNull(midnight.time, FormatStyle.MEDIUM, plain.withHourCycle(cycle)) ?: continue
                checked++
                val hour = leadingNumber(rendered)
                if (hour != MIDNIGHT_HOURS.getValue(cycle)) wrong.add("$tag $cycle read $hour in <$rendered>")
            }
        }
        assertTrue(checked >= patternTags.size * 4, "only $checked of the ${patternTags.size * 4} cases rendered")
        assertTrue(
            wrong.isEmpty(),
            "${wrong.size} of $checked standard patterns write an hour the named cycle does not call for " +
                "at 00:30:45, where h11 is 0, h12 is 12, h23 is 0 and h24 is 24:\n" +
                wrong.take(20).joinToString("\n") { "    $it" },
        )
    }

    test("a skeleton under an hc override agrees with ICU") {
        val comparison = DomainComparison("hour-cycle-skeletons")
        val moved = OverrideEffect()
        for (tag in skeletonTags) {
            val plain = IcuHarness.locale(tag)
            for (cycle in CONCRETE_CYCLES) {
                val locale = plain.withHourCycle(cycle)
                val generator = DateTimePatternGenerator.getInstance(IcuHarness.uLocale(locale.toLanguageTag()))
                for (skeleton in HOUR_SKELETONS) {
                    val ours = CldrDateTimeSkeletons.skeletonPatternOrNull(skeleton, locale) ?: continue
                    if (skeleton == "jm") {
                        moved.record(cycle, ours != CldrDateTimeSkeletons.skeletonPatternOrNull(skeleton, plain))
                    }
                    comparison.compare(tag, "$skeleton/${cycle.name}", ours, generator.getBestPattern(skeleton)) { null }
                }
            }
        }
        moved.assertEveryCycleMoved("the jm skeleton")
        comparison.settle(minimumCompared = skeletonTags.size * 80L)
    }

    test("the j skeleton writes the hour with the letter the cycle names") {
        val wrong = ArrayList<String>()
        var checked = 0
        for (tag in skeletonTags) {
            val plain = IcuHarness.locale(tag)
            for (cycle in CONCRETE_CYCLES) {
                val pattern = CldrDateTimeSkeletons.skeletonPatternOrNull("j", plain.withHourCycle(cycle)) ?: continue
                checked++
                val expected = HOUR_LETTERS.getValue(cycle)
                val found = hourLettersOutsideQuotes(pattern)
                if (found != setOf(expected)) wrong.add("$tag $cycle wanted $expected, pattern is <$pattern>")
            }
        }
        assertTrue(checked >= skeletonTags.size * 4, "only $checked of the ${skeletonTags.size * 4} j cases resolved")
        assertTrue(
            wrong.isEmpty(),
            "${wrong.size} of $checked j patterns write the hour with a letter the cycle did not name:\n" +
                wrong.take(20).joinToString("\n") { "    $it" },
        )
    }

    test("every hour cycle waiver names one of the categories") {
        if (Ledger.isGeneratorRun) return@test
        val ledger = Ledger.open()
        val rows = HOUR_CYCLE_DOMAINS.flatMap { ledger.read(it).values }
        assertTrue(rows.isNotEmpty(), "no hour cycle waivers were read, so this check proves nothing")
        val uncategorised = rows.filter { row -> WAIVER_CATEGORIES.none { row.note.startsWith("$it.") } }
        assertTrue(
            uncategorised.isEmpty(),
            "${uncategorised.size} of ${rows.size} waivers do not open with one of " +
                "${WAIVER_CATEGORIES.joinToString()}:\n" +
                uncategorised.take(20).joinToString("\n") { "    ${it.locale} ${it.case}: ${it.note.take(60)}" },
        )
    }
}

private val HOUR_CYCLE_DOMAINS = listOf("hour-cycle-patterns", "hour-cycle-skeletons")

/**
 * The five reasons a waiver can give, read off the data rather than chosen in
 * advance.
 *
 * Checked rather than written down and trusted: a waiver whose note opens with
 * none of these fails, so a future row cannot be added with a sentence that
 * explains nothing and no category at all. The prose after the token still has
 * to be written by a person, which is what `Ledger.UNEXPLAINED` enforces.
 */
private val WAIVER_CATEGORIES = listOf(
    "ICU_REDERIVES_FROM_AVAILABLE_FORMATS",
    "NON_GREGORIAN_CALENDAR",
    "UNCONFIRMED_DRAFT",
    "KOREAN_TWENTY_FOUR_HOUR_MEDIUM",
    "RECORDED_IN_BOUNDARIES",
    "ICU_PARENT_CHAIN",
)

/** The four cycles that name a pattern letter, which are the four ICU answers for reliably. */
private val CONCRETE_CYCLES = listOf(HourCycle.H11, HourCycle.H12, HourCycle.H23, HourCycle.H24)

/**
 * UTS #35's own letter for each cycle, spelled out rather than read off
 * `HourCycle.patternLetter`.
 *
 * A conformance test that asked the library which letter it meant would agree
 * with itself no matter what the library answered. These four pairs are the
 * table in UTS #35 Part 1 under the `hc` key.
 */
private val HOUR_LETTERS = mapOf(
    HourCycle.H11 to 'K',
    HourCycle.H12 to 'h',
    HourCycle.H23 to 'H',
    HourCycle.H24 to 'k',
)

/**
 * The two instants the standard patterns are rendered at, because one cannot
 * tell the four cycles apart.
 *
 * At 15:30:45 both `K` and `h` print 3 and both `H` and `k` print 15, so a
 * comparison pinned there renders `h11` and `h12` as the same string, and `h23`
 * and `h24` as the same string. Every row would arrive in twin pairs and a swap
 * that dropped `K` or `k` outright would still pass. Half past midnight is where
 * the distinction lives: `h` writes 12, `K` writes 0, `H` writes 0 and `k`
 * writes 24. The two readings together name the letter, since only `K` reads 3
 * then 0 and only `H` reads 15 then 0.
 *
 * CLDR's own `hc` cases use 00:00 and 12:00 as inputs for the same reason, and
 * Task 9's `c12` guard had to move off 15:30 for it.
 *
 * Local to this file rather than added to `Reference.kt`: the midnight reading
 * exists for the hour cycle and nothing else needs it, and the date still comes
 * from the shared constants so the two cannot drift apart.
 */
private class ReferenceInstant(val label: String, val time: LocalTime, val date: java.util.Date)

private val REFERENCE_INSTANTS = listOf(
    ReferenceInstant(
        "15:30:45",
        LocalTime(REFERENCE_HOUR, REFERENCE_MINUTE, REFERENCE_SECOND),
        REFERENCE_DATE,
    ),
    ReferenceInstant(
        "00:30:45",
        LocalTime(0, REFERENCE_MINUTE, REFERENCE_SECOND),
        java.util.Date(utcMillis(REFERENCE_YEAR, REFERENCE_MONTH, REFERENCE_DAY, 0, REFERENCE_MINUTE, REFERENCE_SECOND)),
    ),
)

/**
 * What each cycle writes the hour as at half past midnight, which is the reading
 * that tells all four apart.
 *
 * Read off UTS #35's definitions rather than off the library: `h11` is 0 to 11,
 * `h12` is 1 to 12, `h23` is 0 to 23 and `h24` is 1 to 24.
 */
private val MIDNIGHT_HOURS = mapOf(
    HourCycle.H11 to 0,
    HourCycle.H12 to 12,
    HourCycle.H23 to 0,
    HourCycle.H24 to 24,
)

/**
 * The first run of decimal digits in [text], in any numbering system, or null
 * when it has none.
 *
 * Walked by code point rather than by `Char`. Chakma and Adlam write their
 * digits above the basic plane, so a `Char`-at-a-time reader sees a surrogate
 * half, calls it "not a digit" and reports 64 locales as failures it invented.
 */
private fun leadingNumber(text: String): Int? {
    var value: Int? = null
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val digit = Character.digit(codePoint, 10)
        if (digit < 0) {
            if (value != null) return value
        } else {
            value = (value ?: 0) * 10 + digit
        }
        index += Character.charCount(codePoint)
    }
    return value
}

/** The `h H K k` a pattern writes the hour with, ignoring any inside quoted literal text. */
private fun hourLettersOutsideQuotes(pattern: String): Set<Char> {
    val found = HashSet<Char>()
    var quoted = false
    for (character in pattern) {
        when {
            character == '\'' -> quoted = !quoted
            quoted -> {}
            character in "hHKk" -> found.add(character)
        }
    }
    return found
}

/**
 * MEDIUM and SHORT only, as in `CalendarConformanceTest`: FULL and LONG carry a
 * zone name this library strips, which would make every locale differ for a
 * reason that has nothing to do with the hour.
 */
private val TIME_STYLES = listOf(
    FormatStyle.MEDIUM to DateFormat.MEDIUM,
    FormatStyle.SHORT to DateFormat.SHORT,
)

/**
 * The skeletons whose answer an `hc` override can change.
 *
 * `j` and `C` are the metacharacters the cycle resolves, and `h H K k` are the
 * explicit requests that the same-family nudge applies to. `B` is here because a
 * flexible day period travels with the twelve-hour forms, and a cycle that
 * crossed families while leaving `B` behind would read as correct until someone
 * looked at the output.
 */
private val HOUR_SKELETONS = listOf(
    "j", "jm", "jms", "J", "Jm", "C", "Cm",
    "h", "hm", "hms", "H", "Hm", "Hms", "K", "Km", "k", "km",
    "Bh", "Bhm", "Bhms",
)

/**
 * Counts, per cycle, the cases where naming the cycle changed this library's own
 * answer.
 *
 * The guard against the failure that looks like success. `DomainComparison`
 * already refuses a run that compared too little, and that is not enough here: a
 * regression that dropped the `hc` keyword on the way in would compare the full
 * matrix and agree with ICU everywhere ICU also ignores it. What separates the
 * two is whether the override moved anything at all.
 */
private class OverrideEffect {
    private val moved = HashMap<HourCycle, Int>()

    fun record(cycle: HourCycle, changed: Boolean) {
        if (changed) moved[cycle] = (moved[cycle] ?: 0) + 1
    }

    fun assertEveryCycleMoved(what: String) {
        val counts = CONCRETE_CYCLES.associateWith { moved[it] ?: 0 }
        assertTrue(
            counts.values.min() >= MINIMUM_MOVED,
            "naming a cycle changed $what for too few cases to be an override that reached the " +
                "formatter at all: " + counts.entries.joinToString { (cycle, count) -> "$cycle $count" },
        )
    }
}

/**
 * How many cases a cycle has to move before the override counts as wired.
 *
 * Every cycle crosses the family for a large majority of the catalogue, since
 * CLDR's locales are overwhelmingly one family or the other, so the true numbers
 * are in the hundreds. Two hundred is low enough that a CLDR release reshuffling
 * which locales prefer what cannot trip it.
 */
private const val MINIMUM_MOVED = 200
