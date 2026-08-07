package dev.carcara.kotlinx.locale.icu

import at.asitplus.testballoon.matrix.matrixSuite
import com.ibm.icu.text.DateTimePatternGenerator
import com.ibm.icu.text.DisplayContext
import com.ibm.icu.text.RelativeDateTimeFormatter
import dev.carcara.kotlinx.locale.datetime.RelativeTimeNumbering
import dev.carcara.kotlinx.locale.datetime.RelativeTimeStyle
import dev.carcara.kotlinx.locale.datetime.RelativeTimeUnit
import dev.carcara.kotlinx.locale.datetime.cldr.relative.CldrRelativeTime
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.CldrDateTimeSkeletons
import dev.carcara.kotlinx.locale.test.assertTrue

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
val DateTimeConformanceTest by matrixSuite {

    val skeletonTags = CldrDateTimeSkeletons.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    val relativeTags = CldrRelativeTime.supportedLocales
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
}

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
