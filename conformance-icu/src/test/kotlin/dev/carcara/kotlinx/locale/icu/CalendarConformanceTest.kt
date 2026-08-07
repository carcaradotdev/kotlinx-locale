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
import com.ibm.icu.text.DateFormatSymbols
import com.ibm.icu.text.MeasureFormat
import com.ibm.icu.util.Measure
import com.ibm.icu.util.MeasureUnit
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.NameContext
import dev.carcara.kotlinx.locale.datetime.TextStyle
import dev.carcara.kotlinx.locale.datetime.cldr.CldrDateTime
import dev.carcara.kotlinx.locale.datetime.cldr.durations.CldrDurationUnits
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.DurationUnit
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.UnitWidth
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The calendar tables the committed goldens cover at thirty locales.
 *
 * Month and weekday names are the oldest data in the library and had the
 * narrowest oracle relative to their size: 1122 locales times 114 names, checked
 * against ICU for 30 of them. The standalone forms are the interesting half.
 * Several languages inflect a month name differently when it stands alone than
 * when it sits inside a date, and a generator that read the wrong CLDR element
 * produces something that looks like a plausible month name in both places.
 *
 * Duration units are here rather than with the number domains because they are a
 * calendar vocabulary: ICU answers for them through `MeasureFormat`, which is
 * also where the plural agreement between a count and its unit is decided.
 */
val CalendarConformanceTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val tags = CldrDateTime.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    val durationTags = CldrDurationUnits.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    test("the comparison set is the whole shipped catalogue") {
        assertTrue(
            tags.size > 500,
            "only ${tags.size} locales carry calendar names and are comparable against ICU",
        )
    }

    test("month and weekday names agree with ICU") {
        val comparison = DomainComparison("datetime-names")
        for (tag in tags) {
            val locale = IcuHarness.locale(tag)
            // Built against an explicit Gregorian calendar rather than through
            // `getInstance`, which follows the locale's preferred calendar and
            // would hand back Persian month names for `fa`. Gregorian-only is a
            // documented boundary, so asking ICU the Gregorian question is the
            // comparison that means something rather than a skew to classify.
            val symbols = DateFormatSymbols(gregorianUtc(), IcuHarness.uLocale(tag))
            for ((context, icuContext) in NAME_CONTEXTS) {
                for ((style, icuWidth) in NAME_WIDTHS) {
                    val months = symbols.getMonths(icuContext, icuWidth)
                    for (month in 1..12) {
                        val ours = CldrDateTime.monthNameOrNull(month, style, context, locale) ?: continue
                        comparison.compare(tag, "month/$month/$style/$context", ours, months[month - 1]) { null }
                    }
                    val weekdays = symbols.getWeekdays(icuContext, icuWidth)
                    for (day in 1..7) {
                        val ours = CldrDateTime.dayOfWeekNameOrNull(day, style, context, locale) ?: continue
                        // ICU indexes by Calendar.SUNDAY = 1 through SATURDAY = 7;
                        // this library indexes by ISO day, Monday = 1.
                        comparison.compare(tag, "weekday/$day/$style/$context", ours, weekdays[(day % 7) + 1]) { null }
                    }
                }
            }
        }
        comparison.settle(minimumCompared = tags.size * 50L)
    }

    test("date and time formatting agrees with ICU") {
        val comparison = DomainComparison("datetime-patterns")
        val date = LocalDate(REFERENCE_YEAR, REFERENCE_MONTH, REFERENCE_DAY)
        val time = LocalTime(REFERENCE_HOUR, REFERENCE_MINUTE, REFERENCE_SECOND)
        for (tag in tags) {
            val locale = IcuHarness.locale(tag)
            val uLocale = IcuHarness.uLocale(tag)
            for ((style, icuStyle) in FORMAT_STYLES) {
                val dateFormat = DateFormat.getDateInstance(gregorianUtc(), icuStyle, uLocale)
                dateFormat.timeZone = ICU_UTC
                CldrDateTime.formatDateOrNull(date, style, locale)?.let { ours ->
                    comparison.compare(tag, "date/$style", ours, dateFormat.format(REFERENCE_DATE)) { null }
                }
            }
            // MEDIUM and SHORT only. The FULL and LONG time patterns carry a zone
            // name, which makes them a zone-name comparison wearing a time
            // pattern's clothes, and `TimeZoneConformanceTest` already asks that
            // question against the whole metazone set rather than against UTC.
            for ((style, icuStyle) in TIME_STYLES) {
                val timeFormat = DateFormat.getTimeInstance(gregorianUtc(), icuStyle, uLocale)
                timeFormat.timeZone = ICU_UTC
                CldrDateTime.formatTimeOrNull(time, style, locale)?.let { ours ->
                    comparison.compare(tag, "time/$style", ours, timeFormat.format(REFERENCE_DATE)) { null }
                }
            }
        }
        comparison.settle(minimumCompared = tags.size * 4L)
    }

    test("duration unit names and formatting agree with ICU") {
        val comparison = DomainComparison("duration-units")
        for (tag in durationTags) {
            val locale = IcuHarness.locale(tag)
            val uLocale = IcuHarness.uLocale(tag)
            for ((width, icuWidth) in UNIT_WIDTHS) {
                val formatter = MeasureFormat.getInstance(uLocale, icuWidth)
                for (unit in DurationUnit.entries) {
                    val measureUnit = MEASURE_UNITS[unit] ?: continue

                    CldrDurationUnits.durationUnitNameOrNull(unit, width, locale)?.let { ours ->
                        val theirs = formatter.getUnitDisplayName(measureUnit)
                        if (theirs != null) {
                            comparison.compare(tag, "$unit/$width/name", ours, theirs) {
                                scriptFallback(tag, ours) { other ->
                                    MeasureFormat.getInstance(IcuHarness.uLocale(other), icuWidth)
                                        .getUnitDisplayName(measureUnit)
                                }
                            }
                        }
                    }

                    // A count and its unit, which is where the plural rules and
                    // the unit table have to agree with each other. One and two
                    // separate `one` from `other` everywhere; five reaches `few`
                    // and `many` in the Slavic and Celtic families.
                    for (count in longArrayOf(1, 2, 5)) {
                        val ours = CldrDurationUnits
                            .durationFormatOrNull(Decimal.parse(count.toString()), unit, width, locale)
                            ?: continue
                        val theirs = formatter.format(Measure(count, measureUnit))
                        comparison.compare(tag, "$unit/$width/$count", ours, theirs) {
                            scriptFallback(tag, ours) { other ->
                                MeasureFormat.getInstance(IcuHarness.uLocale(other), icuWidth)
                                    .format(Measure(count, measureUnit))
                            }
                        }
                    }
                }
            }
        }
        comparison.settle(minimumCompared = durationTags.size * 10L)
    }
}

/** The one locale ICU has no bundle for and answers in the wrong script. */
private fun scriptFallback(tag: String, ours: String, lookup: (String) -> String?): Divergence? =
    if (IcuHarness.answeredInAnotherScript(tag, ours, lookup)) Divergence.BUNDLE_FALLBACK else null

private val NAME_CONTEXTS = listOf(
    NameContext.FORMAT to DateFormatSymbols.FORMAT,
    NameContext.STANDALONE to DateFormatSymbols.STANDALONE,
)

private val NAME_WIDTHS = listOf(
    TextStyle.FULL to DateFormatSymbols.WIDE,
    TextStyle.ABBREVIATED to DateFormatSymbols.ABBREVIATED,
    TextStyle.NARROW to DateFormatSymbols.NARROW,
)

private val FORMAT_STYLES = listOf(
    FormatStyle.FULL to DateFormat.FULL,
    FormatStyle.LONG to DateFormat.LONG,
    FormatStyle.MEDIUM to DateFormat.MEDIUM,
    FormatStyle.SHORT to DateFormat.SHORT,
)

private val TIME_STYLES = listOf(
    FormatStyle.MEDIUM to DateFormat.MEDIUM,
    FormatStyle.SHORT to DateFormat.SHORT,
)

private val UNIT_WIDTHS = listOf(
    UnitWidth.LONG to MeasureFormat.FormatWidth.WIDE,
    UnitWidth.SHORT to MeasureFormat.FormatWidth.SHORT,
    UnitWidth.NARROW to MeasureFormat.FormatWidth.NARROW,
)

/**
 * This library's duration units as ICU measure units.
 *
 * Resolved through `forIdentifier` rather than through the `MeasureUnit`
 * constants, because the enum names and the CLDR identifiers are the same word
 * and a hand-written table of fourteen pairs is fourteen chances to write the
 * wrong one. A unit ICU does not carry drops out rather than failing the lookup,
 * which is what keeps this from breaking on the next ICU bump that adds one.
 */
private val MEASURE_UNITS: Map<DurationUnit, MeasureUnit> = DurationUnit.entries.mapNotNull { unit ->
    runCatching { MeasureUnit.forIdentifier(unit.name.lowercase()) }.getOrNull()?.let { unit to it }
}.toMap()
