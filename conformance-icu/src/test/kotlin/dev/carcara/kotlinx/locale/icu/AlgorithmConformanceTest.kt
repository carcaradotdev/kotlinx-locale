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
import com.ibm.icu.number.NumberFormatter
import com.ibm.icu.number.Scale
import com.ibm.icu.text.PluralRules
import com.ibm.icu.util.MeasureUnit
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.PluralType
import dev.carcara.kotlinx.locale.number.cldr.CldrNumber
import dev.carcara.kotlinx.locale.number.cldr.CldrNumberPlurals
import dev.carcara.kotlinx.locale.number.cldr.numberFormat
import dev.carcara.kotlinx.locale.number.cldr.numberFormatPercent
import dev.carcara.kotlinx.locale.number.pluralCategory
import dev.carcara.kotlinx.locale.number.symbols
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * The domains where a table is not the answer and an algorithm is.
 *
 * `NameConformanceTest` compares lookups: a name either matches ICU's or it does
 * not, and a difference is a data question. These are the other kind. Plural
 * selection is a rule evaluator, number formatting is a pattern parser plus a
 * renderer plus a rounding mode, and a bug in either is a bug that no amount of
 * table checking finds.
 *
 * They run at the same breadth as the name domains, all eleven hundred locales,
 * which is the point: the committed goldens cover thirty, and the rules that go
 * wrong are rarely the ones a person would have picked for a sample.
 */
val AlgorithmConformanceTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val pluralTags = CldrNumberPlurals.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    // Not the same set. Plural rules are declared per language and there are 227
    // of them; symbols and patterns are per locale and there are 1122. Driving
    // the symbol comparison off the plural set was a copied line, and it held
    // that domain to 188 locales of the 1122 it ships while reporting green.
    val numberTags = CldrNumber.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    test("the comparison set is the whole shipped catalogue") {
        assertTrue(
            pluralTags.size > 100,
            "only ${pluralTags.size} locales are comparable against ICU's plural rules, which suggests the " +
                "availability filter is wrong rather than that ICU shrank",
        )
        assertTrue(
            numberTags.size > 500,
            "only ${numberTags.size} locales carry number symbols and are comparable against ICU",
        )
    }

    test("cardinal plural selection agrees with ICU") {
        comparePlurals("plural-cardinal", pluralTags, PluralType.CARDINAL, PluralRules.PluralType.CARDINAL)
    }

    test("ordinal plural selection agrees with ICU") {
        comparePlurals("plural-ordinal", pluralTags, PluralType.ORDINAL, PluralRules.PluralType.ORDINAL)
    }

    test("number formatting agrees with ICU") {
        // Symbols are a table and this is the renderer that composes them. A
        // locale can have every symbol right and still place the grouping wrong,
        // because the grouping width, the minimum grouping digits and the
        // negative pattern come from the pattern rather than from the symbols.
        val comparison = DomainComparison("number-formats")
        for (tag in numberTags) {
            val locale = IcuHarness.locale(tag)
            val uLocale = IcuHarness.uLocale(tag)
            // `NumberFormatter`, not `NumberFormat.getInstance`. The legacy
            // formatter does not apply `minimumGroupingDigits`, so it writes
            // Belarusian 1234 as `1 234` where CLDR says the group separator
            // starts at five digits. Asked the old way, this comparison reported
            // 1234 divergences in 78 locales and every one of them was the oracle
            // being wrong.
            val decimal = NumberFormatter.withLocale(uLocale)
            val percent = NumberFormatter.withLocale(uLocale)
                .unit(MeasureUnit.PERCENT)
                .scale(Scale.powerOfTen(2))

            for (text in NUMBER_SAMPLES) {
                val value = Decimal.parse(text)
                val ours = numberFormat(value, locale)
                comparison.compare(tag, "decimal/$text", ours, decimal.format(java.math.BigDecimal(text)).toString()) { null }
            }
            for (text in PERCENT_SAMPLES) {
                val value = Decimal.parse(text)
                val ours = numberFormatPercent(value, locale)
                comparison.compare(tag, "percent/$text", ours, percent.format(java.math.BigDecimal(text)).toString()) { null }
            }
        }
        comparison.settle(minimumCompared = numberTags.size * 10L)
    }

    test("number symbols agree with ICU") {
        val comparison = DomainComparison("number-symbols")
        for (tag in numberTags) {
            val locale = IcuHarness.locale(tag)
            val ours = CldrNumber.symbols(locale)
            val icu = com.ibm.icu.text.DecimalFormatSymbols.getInstance(IcuHarness.uLocale(tag))

            comparison.compare(tag, "decimal", ours.decimal, icu.decimalSeparatorString) { null }
            comparison.compare(tag, "group", ours.group, icu.groupingSeparatorString) { null }
            comparison.compare(tag, "minus", ours.minusSign, icu.minusSignString) { null }
            comparison.compare(tag, "percent", ours.percentSign, icu.percentString) { null }
            // The digit set, as one case rather than ten: a numbering system is
            // chosen whole and a locale that took nine digits from one system and
            // one from another would be a stranger bug than the comparison can
            // usefully describe per digit.
            comparison.compare(tag, "digits", ours.digits.joinToString(""), icu.digitStrings.joinToString("")) { null }
        }
        // Five symbols per locale, so this is a floor on the locale count rather
        // than a large number for its own sake.
        comparison.settle(minimumCompared = numberTags.size * 4L)
    }
}

/**
 * Values chosen for the rendering branches.
 *
 * The magnitudes straddle every grouping width in CLDR: three digits needs none,
 * four is where a locale with `minimumGroupingDigits` of two still writes none,
 * five upward is where the Indic three-two-two grouping separates from the
 * Western three-three. The negatives exercise the pattern's second half, which
 * several locales write with a prefix rather than a sign.
 */
private val NUMBER_SAMPLES = listOf(
    "0", "1", "-1", "12", "123", "1234", "12345", "123456", "1234567", "-1234567",
    "0.5", "-0.5", "1.25", "1234.5",
)

/**
 * Fractions rather than percentages, which is what `numberFormatPercent` takes.
 *
 * Kept to values that are exact at two decimal places, because ICU's percent
 * instance rounds to whole percent by default and comparing rounding modes is a
 * different question from comparing patterns.
 */
private val PERCENT_SAMPLES = listOf("0", "0.01", "0.25", "0.5", "1", "-0.25", "2.5")

/**
 * Holds this library's plural evaluator to ICU's for the same values.
 *
 * The sample values matter more than their count. Plural rules branch on the
 * integer value, the count of visible fraction digits and the fraction digits
 * themselves, so `1`, `1.0` and `1.00` are one quantity and can be three
 * categories. A list of whole numbers would exercise one operand of five.
 */
private fun comparePlurals(domain: String, tags: List<String>, ourType: PluralType, icuType: PluralRules.PluralType) {
    val comparison = DomainComparison(domain)
    for (tag in tags) {
        val locale = IcuHarness.locale(tag)
        val rules = PluralRules.forLocale(IcuHarness.uLocale(tag), icuType)
        for (text in PLURAL_SAMPLES) {
            val value = Decimal.parse(text)
            val ours = CldrNumberPlurals.pluralCategory(value, value.scale, locale, ourType).cldrName
            // ICU's keyword for the same written value. The double loses the
            // visible-digit count, so the operands are passed explicitly through
            // the string form ICU parses.
            val icu = rules.select(PluralRules.FixedDecimal(text.toDouble(), value.scale))
            comparison.compare(tag, "$text", ours, icu) { null }
        }
    }
    comparison.settle(minimumCompared = 5_000)
}

/**
 * Values chosen for the operands rather than for the numbers.
 *
 * Zero and one for the languages that treat them specially, the teens and the
 * `x1`/`x2` endings for the Slavic and Celtic families, and the same quantity
 * written with and without trailing zeros for the visible-digit operands that
 * separate Czech's `one` from its `many`.
 */
private val PLURAL_SAMPLES = listOf(
    "0", "1", "2", "3", "5", "6", "10", "11", "12", "13", "17", "20", "21", "22", "23",
    "100", "101", "102", "111", "1000", "1001",
    "0.0", "1.0", "1.00", "1.5", "2.0", "2.5", "0.1", "0.5", "10.0", "100.0",
)
