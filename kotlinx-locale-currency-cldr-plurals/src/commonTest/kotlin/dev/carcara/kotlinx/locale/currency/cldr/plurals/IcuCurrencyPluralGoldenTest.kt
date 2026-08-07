package dev.carcara.kotlinx.locale.currency.cldr.plurals

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.cldr.plurals.conformance.icuCurrencyPluralGoldenData
import dev.carcara.kotlinx.locale.currency.cldr.plurals.conformance.icuCurrencyPluralGoldenMinorUnits
import dev.carcara.kotlinx.locale.currency.cldr.plurals.conformance.icuCurrencyPluralGoldenPrecisions
import dev.carcara.kotlinx.locale.currency.cldr.runtime.pluralName
import dev.carcara.kotlinx.locale.currency.forCodeOrNull
import dev.carcara.kotlinx.locale.number.PluralCategory
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * Holds the name form to what ICU writes for the same amount.
 *
 * Every step of the composition meets here: which category the printed number
 * falls into, which of the six spellings that category reaches through the
 * fallback chain, which of the six patterns joins the number to it, and the
 * plain number pattern the digits are written through.
 *
 * Nothing is excluded. The currency output goldens skip a pair whose symbol
 * moved between CLDR `release-48-2` and the snapshot ICU 78.3 was built from,
 * because such a pair would report a data difference as a formatting bug. No
 * currency name in the golden set moved, so this compares all 23,400 of them,
 * and [everyGoldenNameMatchesIcu] is what fails first if one ever does.
 */
val IcuCurrencyPluralGoldenTest by matrixSuite {

    /** ICU and CLDR point releases disagree about which no-break space they use. */
    fun String.normalizedSpaces(): String = replace(' ', ' ').replace(' ', ' ')

    test("namedOutputMatchesIcu") {
        var compared = 0
        val mismatches = ArrayList<String>()
        for ((tag, golden) in icuCurrencyPluralGoldenData) {
            val locale = Locale.forLanguageTag(tag)
            for ((key, expected) in golden.answers) {
                val currency = Currency.forCodeOrNull(key.substringBefore('|')) ?: continue
                val fractionDigits = icuCurrencyPluralGoldenPrecisions[key.substringAfter('|')]
                for ((index, minorUnits) in icuCurrencyPluralGoldenMinorUnits.withIndex()) {
                    val actual = CurrencyAmount(currency, minorUnits).formatPluralName(locale, fractionDigits = fractionDigits)
                    if (expected[index].normalizedSpaces() != actual.normalizedSpaces()) {
                        mismatches.add("$tag $key $minorUnits: icu='${expected[index]}' ours='$actual'")
                    }
                    compared++
                }
            }
        }
        assertTrue(compared > 20000, "expected the goldens to compare twenty thousand strings, compared $compared")
        // Every difference rather than the first, because these arrive as one
        // cause with hundreds of faces: a category chosen wrongly is every
        // amount in the locale that falls into it.
        assertTrue(
            mismatches.isEmpty(),
            "${mismatches.size} of $compared outputs differ from ICU:\n" + mismatches.take(10).joinToString("\n"),
        )
    }

    test("everyGoldenNameMatchesIcu") {
        // The tables behind the output, checked on their own so that a CLDR or
        // ICU bump reports a name that moved as a name that moved rather than as
        // a thousand formatting failures.
        val differences = ArrayList<String>()
        for ((tag, golden) in icuCurrencyPluralGoldenData) {
            val locale = Locale.forLanguageTag(tag)
            for ((key, icu) in golden.names) {
                val currency = Currency.forCodeOrNull(key.substringBefore('|')) ?: continue
                val category = PluralCategory.forCldrNameOrNull(key.substringAfter('|')) ?: continue
                val ours = CldrCurrencyPlurals.pluralName(currency, category, locale)
                if (ours.normalizedSpaces() != icu.normalizedSpaces()) {
                    differences.add("$tag $key: icu='$icu' ours='$ours'")
                }
            }
        }
        assertTrue(
            differences.isEmpty(),
            "${differences.size} names differ from ICU:\n" + differences.take(10).joinToString("\n"),
        )
    }
}
