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
import com.ibm.icu.text.DateIntervalFormat
import com.ibm.icu.text.DateTimePatternGenerator
import com.ibm.icu.text.DisplayContext
import com.ibm.icu.text.RelativeDateTimeFormatter
import com.ibm.icu.util.DateInterval
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.datetime.RelativeTimeNumbering
import dev.carcara.kotlinx.locale.datetime.RelativeTimeStyle
import dev.carcara.kotlinx.locale.datetime.RelativeTimeUnit
import dev.carcara.kotlinx.locale.datetime.cldr.intervals.CldrDateTimeIntervals
import dev.carcara.kotlinx.locale.datetime.cldr.relative.CldrRelativeTime
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.CldrDateTimeSkeletons
import dev.carcara.kotlinx.locale.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * The two datetime domains a committed golden covers worst.
 *
 * Skeletons had the widest golden in the build, 904 locales of 1122, and this
 * closes the remaining 218. Relative time had no oracle at all: the table ships
 * for every locale and nothing had ever compared a word of it to anything.
 *
 * Both are here rather than in a golden for the same reason as the name domains.
 * A skeleton golden wide enough for every locale is half a megabyte of Kotlin in
 * a native test binary; asking ICU the same question on the JVM costs nothing to
 * store.
 */
val DateTimeConformanceTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val skeletonTags = CldrDateTimeSkeletons.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    val relativeTags = CldrRelativeTime.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    val intervalTags = CldrDateTimeIntervals.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    test("the skeleton comparison set is the whole shipped catalogue") {
        assertTrue(
            skeletonTags.size > 500,
            "only ${skeletonTags.size} locales carry skeletons and are comparable against ICU",
        )
    }

    test("skeleton pattern selection agrees with ICU") {
        // Patterns rather than formatted output, and deliberately: the matcher's
        // whole job is picking a pattern, and comparing rendered text would let a
        // wrong pattern pass whenever the sample date happened to render the same.
        val comparison = DomainComparison("skeletons")
        for (tag in skeletonTags) {
            val locale = IcuHarness.locale(tag)
            val generator = DateTimePatternGenerator.getInstance(IcuHarness.uLocale(tag))
            for (skeleton in SKELETONS) {
                val ours = CldrDateTimeSkeletons.skeletonPatternOrNull(skeleton, locale) ?: continue
                val icu = generator.getBestPattern(skeleton)
                comparison.compare(tag, skeleton, ours, icu) { calendarSkew(tag) }
            }
        }
        comparison.settle(minimumCompared = skeletonTags.size * 10L)
    }

    test("relative time wording agrees with ICU") {
        val comparison = DomainComparison("relative-time")
        for (tag in relativeTags) {
            val locale = IcuHarness.locale(tag)
            val uLocale = IcuHarness.uLocale(tag)
            for ((style, icuStyle) in RELATIVE_STYLES) {
                val formatter = RelativeDateTimeFormatter.getInstance(
                    uLocale,
                    null,
                    icuStyle,
                    // Not null: ICU dereferences the context inside format.
                    // NONE is the neutral setting and matches this library,
                    // which capitalizes through Capitalization on request
                    // rather than inside the formatter.
                    DisplayContext.CAPITALIZATION_NONE,
                )
                for ((unit, icuUnit) in RELATIVE_UNITS) {
                    for (value in longArrayOf(-2, -1, 0, 1, 2)) {
                        val ours = CldrRelativeTime.formatOrNull(
                            value,
                            unit,
                            style,
                            // ALWAYS pairs with ICU's `formatNumeric`. The AUTO
                            // form substitutes "yesterday" for -1 day and is
                            // ICU's plain `format`, a different comparison.
                            RelativeTimeNumbering.ALWAYS,
                            locale,
                        ) ?: continue
                        val icu = formatter.formatNumeric(value.toDouble(), icuUnit)
                        comparison.compare(tag, "$style/$unit/$value", ours, icu) { null }
                    }
                }
            }
        }
        comparison.settle(minimumCompared = relativeTags.size * 20L)
    }

    test("interval formatting agrees with ICU") {
        // Intervals had a golden at 905 locales and no live comparison, which
        // sounds covered and is not: the golden was cut from the same ICU the
        // generator ran against, so it re-asserts what the generator did rather
        // than checking it. Asking ICU now is what makes it an oracle.
        val comparison = DomainComparison("intervals")
        for (tag in intervalTags) {
            val locale = IcuHarness.locale(tag)
            val uLocale = IcuHarness.uLocale(tag)
            for (skeleton in INTERVAL_SKELETONS) {
                val formatter = DateIntervalFormat.getInstance(skeleton, uLocale)
                formatter.setTimeZone(ICU_UTC)
                for (range in DATE_RANGES) {
                    val ours = CldrDateTimeIntervals
                        .intervalFormatOrNull(range.fromDate, range.toDate, skeleton, locale)
                        ?: continue
                    val theirs = formatter.format(DateInterval(range.fromMillis, range.toMillis))
                    comparison.compare(tag, "$skeleton/${range.name}", ours, theirs) {
                        calendarSkew(tag) ?: widening(skeleton, range.differingField)
                    }
                }
            }
        }
        comparison.settle(minimumCompared = intervalTags.size * 5L)
    }
}

/**
 * The skeletons where the two endpoints differ in a field that matters.
 *
 * An interval pattern is chosen by the largest calendar field the two ends
 * disagree on, so the useful cases are the ones that cross a boundary: same day,
 * across a day, across a month, across a year. A skeleton with no field for the
 * boundary being crossed falls back to formatting both ends whole, which is the
 * other branch worth exercising.
 */
private val INTERVAL_SKELETONS = listOf("yMd", "yMMMd", "yMMMEd", "MMMd", "Md", "yM", "yMMM", "y", "d")

/**
 * Whether this case is one where neither side could render the difference.
 *
 * Read off the inputs rather than off the answers: the skeleton either names the
 * field the two endpoints differ in or it does not, and that is decided before
 * anything is formatted. Classifying on the shape of the output instead would be
 * the classifier deciding a real disagreement looked close enough.
 */
private fun widening(skeleton: String, differingField: Char): Divergence? =
    if (differingField in skeleton) null else Divergence.WIDENED_FALLBACK

/** One pair of dates per calendar field an interval pattern can turn on. */
private class DateRange(
    val name: String,
    val differingField: Char,
    fromYear: Int,
    fromMonth: Int,
    fromDay: Int,
    toYear: Int,
    toMonth: Int,
    toDay: Int,
) {
    val fromDate: LocalDate = LocalDate(fromYear, fromMonth, fromDay)
    val toDate: LocalDate = LocalDate(toYear, toMonth, toDay)
    val fromMillis: Long = utcMillis(fromYear, fromMonth, fromDay)
    val toMillis: Long = utcMillis(toYear, toMonth, toDay)
}

private val DATE_RANGES = listOf(
    DateRange("same-day", 'd', REFERENCE_YEAR, REFERENCE_MONTH, REFERENCE_DAY, REFERENCE_YEAR, REFERENCE_MONTH, REFERENCE_DAY),
    DateRange("across-days", 'd', REFERENCE_YEAR, REFERENCE_MONTH, REFERENCE_DAY, REFERENCE_YEAR, REFERENCE_MONTH, REFERENCE_DAY + 3),
    DateRange("across-months", 'M', REFERENCE_YEAR, REFERENCE_MONTH, REFERENCE_DAY, REFERENCE_YEAR, REFERENCE_MONTH + 2, REFERENCE_DAY),
    DateRange("across-years", 'y', REFERENCE_YEAR, REFERENCE_MONTH, REFERENCE_DAY, REFERENCE_YEAR + 1, REFERENCE_MONTH, REFERENCE_DAY),
)

/**
 * The skeletons worth asking about.
 *
 * A subset rather than all 107, because this runs eleven hundred times and
 * `DateTimePatternGenerator` is the expensive object in ICU. These are the ones
 * the field-width and day-period logic turns on: `j` picks the locale's own hour
 * cycle, `B` and `b` are the flexible day periods, and the `MMM`/`MMMM` pairs
 * separate a width the locale wrote from one the matcher widened.
 */
private val SKELETONS = listOf(
    "yMd", "yMMMd", "yMMMMd", "yMMMEd", "MMMd", "MMMMd", "Md", "MEd",
    "Hm", "Hms", "hm", "hms", "jm", "jms", "j", "H", "h",
    "Bh", "Bhm", "y", "yM", "yMMM", "yQQQ", "Ed", "d", "E",
)

/**
 * Whether ICU answered for a different calendar than this library implements.
 *
 * `fa` and `ckb-IR` default to the Persian calendar, and a few others to
 * Buddhist or Islamic. ICU's pattern generator follows the locale's preference,
 * so it writes an era field a Gregorian answer does not: `G y` where this
 * library says `y`. Gregorian-only is a documented scope boundary rather than a
 * defect, so these classify rather than land in the ledger one row at a time.
 *
 * Derived rather than listed, because the preferred calendar moves with CLDR and
 * a hand-written set of locales would rot silently.
 */
private fun calendarSkew(tag: String): Divergence? {
    val calendar = com.ibm.icu.util.Calendar.getInstance(IcuHarness.uLocale(tag)).type
    return if (calendar != "gregorian") Divergence.ICU_PRUNED else null
}

private val RELATIVE_STYLES = listOf(
    RelativeTimeStyle.FULL to RelativeDateTimeFormatter.Style.LONG,
    RelativeTimeStyle.SHORT to RelativeDateTimeFormatter.Style.SHORT,
    RelativeTimeStyle.NARROW to RelativeDateTimeFormatter.Style.NARROW,
)

private val RELATIVE_UNITS = listOf(
    RelativeTimeUnit.SECOND to RelativeDateTimeFormatter.RelativeDateTimeUnit.SECOND,
    RelativeTimeUnit.MINUTE to RelativeDateTimeFormatter.RelativeDateTimeUnit.MINUTE,
    RelativeTimeUnit.HOUR to RelativeDateTimeFormatter.RelativeDateTimeUnit.HOUR,
    RelativeTimeUnit.DAY to RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY,
    RelativeTimeUnit.WEEK to RelativeDateTimeFormatter.RelativeDateTimeUnit.WEEK,
    RelativeTimeUnit.MONTH to RelativeDateTimeFormatter.RelativeDateTimeUnit.MONTH,
    RelativeTimeUnit.QUARTER to RelativeDateTimeFormatter.RelativeDateTimeUnit.QUARTER,
    RelativeTimeUnit.YEAR to RelativeDateTimeFormatter.RelativeDateTimeUnit.YEAR,
)
