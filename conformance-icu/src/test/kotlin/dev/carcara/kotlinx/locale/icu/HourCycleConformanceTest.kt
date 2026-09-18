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

    test("a standard time pattern under an hc override agrees with ICU") {
        val comparison = DomainComparison("hour-cycle-patterns")
        val time = LocalTime(REFERENCE_HOUR, REFERENCE_MINUTE, REFERENCE_SECOND)
        val moved = OverrideEffect()
        for (tag in patternTags) {
            val plain = IcuHarness.locale(tag)
            for ((style, icuStyle) in TIME_STYLES) {
                val unoverridden = CldrDateTime.formatTimeOrNull(time, style, plain)
                for (cycle in CONCRETE_CYCLES) {
                    val locale = plain.withHourCycle(cycle)
                    val ours = CldrDateTime.formatTimeOrNull(time, style, locale) ?: continue
                    moved.record(cycle, ours != unoverridden)
                    val format = DateFormat.getTimeInstance(gregorianUtc(), icuStyle, IcuHarness.uLocale(locale.toLanguageTag()))
                    format.timeZone = ICU_UTC
                    comparison.compare(tag, "time/$style/${cycle.name}", ours, format.format(REFERENCE_DATE)) { null }
                }
            }
        }
        moved.assertEveryCycleMoved("standard time patterns")
        comparison.settle(minimumCompared = patternTags.size * 8L)
    }

    test("a skeleton under an hc override agrees with ICU") {
        val comparison = DomainComparison("hour-cycle-skeletons")
        val moved = OverrideEffect()
        for (tag in skeletonTags) {
            val plain = IcuHarness.locale(tag)
            val unoverridden = lazy { DateTimePatternGenerator.getInstance(IcuHarness.uLocale(tag)) }
            for (cycle in CONCRETE_CYCLES) {
                val locale = plain.withHourCycle(cycle)
                val generator = DateTimePatternGenerator.getInstance(IcuHarness.uLocale(locale.toLanguageTag()))
                for (skeleton in HOUR_SKELETONS) {
                    val ours = CldrDateTimeSkeletons.skeletonPatternOrNull(skeleton, locale) ?: continue
                    if (skeleton == "jm") {
                        moved.record(cycle, ours != CldrDateTimeSkeletons.skeletonPatternOrNull(skeleton, plain))
                    }
                    comparison.compare(tag, "$skeleton/${cycle.name}", ours, generator.getBestPattern(skeleton)) {
                        calendarSkew(tag, skeleton, unoverridden)
                    }
                }
            }
        }
        moved.assertEveryCycleMoved("the jm skeleton")
        comparison.settle(minimumCompared = skeletonTags.size * 40L)
    }
}

/** The four cycles that name a pattern letter, which are the four ICU answers for reliably. */
private val CONCRETE_CYCLES = listOf(HourCycle.H11, HourCycle.H12, HourCycle.H23, HourCycle.H24)

/**
 * Whether ICU answered [skeleton] out of a calendar this library does not
 * implement, and was already doing so before any cycle was named.
 *
 * `DateTimeConformanceTest` excuses the first half alone for the un-overridden
 * skeleton comparison, and that is too blunt here. Persian and Buddhist locales
 * declare their hour items on the generic calendar rather than the Gregorian
 * one, so `ps` writes `H:mm` there against root's `HH:mm` and `th` drops the
 * `น.` that its Gregorian `Hm` carries. A cycle that broke exactly those
 * locales and nothing else would be waved through by a calendar check alone.
 *
 * Requiring both halves means the excuse only ever covers a disagreement the
 * override did not create. Anything the override did create is listed.
 */
private fun calendarSkew(tag: String, skeleton: String, unoverridden: Lazy<DateTimePatternGenerator>): Divergence? {
    if (com.ibm.icu.util.Calendar.getInstance(IcuHarness.uLocale(tag)).type == "gregorian") return null
    val ours = CldrDateTimeSkeletons.skeletonPatternOrNull(skeleton, IcuHarness.locale(tag)) ?: return null
    val icu = unoverridden.value.getBestPattern(skeleton)
    return if (ours.normalizedSpaces() != icu.normalizedSpaces()) Divergence.ICU_PRUNED else null
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
