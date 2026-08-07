package dev.carcara.kotlinx.locale.icu

import at.asitplus.testballoon.matrix.matrixSuite
import com.ibm.icu.text.PluralRules
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.PluralType
import dev.carcara.kotlinx.locale.number.cldr.CldrNumber
import dev.carcara.kotlinx.locale.number.cldr.CldrNumberPlurals
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
val AlgorithmConformanceTest by matrixSuite {

    val tags = CldrNumberPlurals.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    test("the comparison set is the whole shipped catalogue") {
        assertTrue(
            tags.size > 100,
            "only ${tags.size} locales are comparable against ICU's plural rules, which suggests the " +
                "availability filter is wrong rather than that ICU shrank",
        )
    }

    test("cardinal plural selection agrees with ICU") {
        comparePlurals("plural-cardinal", tags, PluralType.CARDINAL, PluralRules.PluralType.CARDINAL)
    }

    test("ordinal plural selection agrees with ICU") {
        comparePlurals("plural-ordinal", tags, PluralType.ORDINAL, PluralRules.PluralType.ORDINAL)
    }

    test("number symbols agree with ICU") {
        val comparison = DomainComparison("number-symbols")
        for (tag in tags) {
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
        comparison.settle(minimumCompared = tags.size * 4L)
    }
}

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
