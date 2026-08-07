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
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.cldr.format
import dev.carcara.kotlinx.locale.currency.cldr.plurals.CldrCurrencyPlurals
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.number.PluralCategory
import dev.carcara.kotlinx.locale.test.assertTrue
import java.math.BigDecimal

/**
 * Currency rendering, which is where three tables have to agree at once.
 *
 * A formatted amount is the currency pattern, the number symbols and the symbol
 * or code for that currency in that locale, composed. Each of the three has its
 * own comparison elsewhere and all three can be individually right while the
 * composition is wrong: the sign position, the space between symbol and digits,
 * and which of the two currency patterns a locale uses are decided here and
 * nowhere else.
 *
 * The committed golden covers fifty locales. This covers every locale ICU can
 * answer for, which is the point, since the currency pattern is one of the
 * fields CLDR inherits most aggressively and an inheritance bug shows up in the
 * locales nobody picked as a sample.
 */
val CurrencyFormatConformanceTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val formatTags = CldrCurrency.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    val pluralTags = CldrCurrencyPlurals.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    // Resolved once. `Currency.getInstance` is a hash lookup, but three hundred
    // of them inside a loop over nine hundred locales is not.
    val icuCurrencies = Currency.entries.mapNotNull { currency ->
        com.ibm.icu.util.Currency.getInstance(currency.code)?.let { currency to it }
    }.toMap()

    test("the comparison set is the whole shipped catalogue") {
        assertTrue(
            formatTags.size > 500,
            "only ${formatTags.size} locales carry currency formats and are comparable against ICU",
        )
    }

    test("minor unit counts agree with ICU") {
        // Every currency, and once rather than per locale. How many minor units
        // a currency has is a property of the currency; asking it inside the
        // locale loop was this test's own first bug, and it turned one
        // disagreement about XAU into 905 identical rows that read like a
        // widespread formatting failure.
        val comparison = DomainComparison("currency-minor-units")
        for ((currency, icuCurrency) in icuCurrencies) {
            val ours = CurrencyAmount(currency, 1).toDecimalString().substringAfter('.', "").length
            comparison.compare(
                "und",
                currency.code,
                ours.toString(),
                icuCurrency.defaultFractionDigits.toString(),
            ) { null }
        }
        comparison.settle(minimumCompared = 100)
    }

    test("currency formatting agrees with ICU") {
        val comparison = DomainComparison("currency-formats")
        for (tag in formatTags) {
            val locale = IcuHarness.locale(tag)
            val formatter = NumberFormatter.withLocale(IcuHarness.uLocale(tag))
            for (currency in FORMAT_CURRENCIES) {
                val icuCurrency = icuCurrencies[currency] ?: continue
                val format = formatter.unit(icuCurrency)
                for (minorUnits in AMOUNTS) {
                    val amount = CurrencyAmount(currency, minorUnits)
                    // Both sides are handed the same quantity, written out by this
                    // library and parsed back by ICU, so what is left to disagree
                    // about is the rendering and nothing else.
                    val ours = amount.format(locale)
                    val theirs = format.format(BigDecimal(amount.toDecimalString())).toString()
                    comparison.compare(tag, "${currency.code}/$minorUnits", ours, theirs) {
                        answeredFromAncestor(tag, currency, icuCurrency, amount, ours, theirs)
                    }
                }
            }
        }
        comparison.settle(minimumCompared = formatTags.size * 10L)
    }

    test("currency plural names agree with ICU") {
        val comparison = DomainComparison("currency-plural-names")
        val isChoiceFormat = BooleanArray(1)
        for (tag in pluralTags) {
            val locale = IcuHarness.locale(tag)
            val uLocale = IcuHarness.uLocale(tag)
            // Every currency rather than a sample, because the loop is a map
            // lookup that stops at the first null for the overwhelming majority
            // of pairs, and only the ones this library actually ships a name for
            // reach ICU.
            for (currency in Currency.entries) {
                val icuCurrency = icuCurrencies[currency] ?: continue
                for (category in PluralCategory.entries) {
                    val ours = CldrCurrencyPlurals
                        .currencyPluralNameOrNull(currency.code, category, locale)
                        ?: continue
                    val theirs = icuCurrency.getName(
                        uLocale,
                        com.ibm.icu.util.Currency.PLURAL_LONG_NAME,
                        category.cldrName,
                        isChoiceFormat,
                    ) ?: continue
                    comparison.compare(tag, "${currency.code}/${category.cldrName}", ours, theirs) { null }
                }
            }
        }
        comparison.settle(minimumCompared = pluralTags.size * 5L)
    }
}

/**
 * Whether ICU rendered this from an ancestor's data while CLDR gave the locale
 * its own.
 *
 * `en-150` is in `ULocale.getAvailableLocales`, so the availability filter lets
 * it through, and ICU still writes `€1,234.56` for it. CLDR's own `en_150.xml`
 * declares `#,##0.00 ¤`, which is what this library writes. Availability is per
 * locale and coverage is per element, so a locale can be carried and still have
 * this particular field answered from further up.
 *
 * The test is whether ICU gave the parent's answer where this library did not:
 * that is ICU declining to use data the locale has, which is a non-comparison
 * rather than a disagreement. It is deliberately narrow. Where both sides agree
 * with the parent nothing reaches here at all, and where ICU answers something
 * that is neither the locale's nor the parent's, this returns null and the case
 * goes to the ledger for a person.
 */
private fun answeredFromAncestor(
    tag: String,
    currency: Currency,
    icuCurrency: com.ibm.icu.util.Currency,
    amount: CurrencyAmount,
    ours: String,
    theirs: String,
): Divergence? {
    val parent = tag.substringBeforeLast('-', "").takeIf { it.isNotEmpty() && it != tag } ?: return null
    val parentIcu = NumberFormatter.withLocale(IcuHarness.uLocale(parent))
        .unit(icuCurrency)
        .format(BigDecimal(amount.toDecimalString()))
        .toString()
    if (theirs.normalizedSpaces() != parentIcu.normalizedSpaces()) return null
    val parentOurs = CurrencyAmount(currency, amount.minorUnits).format(IcuHarness.locale(parent))
    if (ours.normalizedSpaces() == parentOurs.normalizedSpaces()) return null
    return Divergence.BUNDLE_FALLBACK
}

/**
 * Currencies chosen for their minor unit counts rather than for their economies.
 *
 * Two digits is the common case and proves nothing on its own. JPY and CLP have
 * none, BHD and TND have three, and those are the ones where a renderer that
 * assumed two produces an answer wrong by a factor of ten or a thousand. XOF has
 * no symbol in most locales, which sends the formatter down the
 * code-instead-of-symbol path where the currency sits next to a digit as a word.
 *
 * The metals are not here. ICU reports two minor units for XAU where ISO 4217
 * gives it none, so every rendered amount would disagree for a reason that has
 * nothing to do with rendering. `currency-minor-units` records that
 * disagreement once, which is where it belongs.
 */
private val FORMAT_CURRENCIES = listOfNotNull(
    Currency.entries.firstOrNull { it.code == "USD" },
    Currency.entries.firstOrNull { it.code == "EUR" },
    Currency.entries.firstOrNull { it.code == "JPY" },
    Currency.entries.firstOrNull { it.code == "BHD" },
    Currency.entries.firstOrNull { it.code == "CLP" },
    Currency.entries.firstOrNull { it.code == "TND" },
    Currency.entries.firstOrNull { it.code == "INR" },
    Currency.entries.firstOrNull { it.code == "XOF" },
)

/**
 * Amounts in minor units, chosen for the branches rather than for the values.
 *
 * Zero and one for the plural boundary, a negative for the sign position, and
 * two magnitudes that straddle the grouping width so that the Indic three-two-two
 * grouping separates from the Western three-three.
 */
private val AMOUNTS = longArrayOf(0, 1, 123456, -123456, 100000000)
