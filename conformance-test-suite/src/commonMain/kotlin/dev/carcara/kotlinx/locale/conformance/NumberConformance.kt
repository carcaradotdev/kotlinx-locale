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

package dev.carcara.kotlinx.locale.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.NumberFormatSource
import dev.carcara.kotlinx.locale.number.NumberGrouping
import dev.carcara.kotlinx.locale.number.NumberNotation
import dev.carcara.kotlinx.locale.number.PluralRuleSource
import dev.carcara.kotlinx.locale.number.PluralType
import dev.carcara.kotlinx.locale.number.SignDisplay
import dev.carcara.kotlinx.locale.number.format
import dev.carcara.kotlinx.locale.number.formatPercent
import dev.carcara.kotlinx.locale.number.pluralCategory
import dev.carcara.kotlinx.locale.number.symbols
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Holds a plural source to CLDR's own samples.
 *
 * `plurals.xml` and `ordinals.xml` write an `@integer` and `@decimal` list under
 * every rule saying which values fall into it. Asserting that the shipped
 * evaluator agrees is CLDR checking this library with CLDR's data, independently
 * of ICU, on every target this compiles for. It is the cheapest high-value
 * fixture in the number domain, because plural selection is a pure function and
 * the samples are a complete statement of intent.
 *
 * The samples keep their written form rather than being parsed into numbers: in
 * Czech `1` is `one` and `1.0` is `many`, and the difference is the count of
 * visible fraction digits, which a numeric type would lose.
 */
public fun PluralRuleSource.assertConformsToCldrPluralSamples() {
    assertTrue(cldrPluralSamples.size > 1000, "expected the full sample set, got ${cldrPluralSamples.size}")
    var checked = 0
    for (sample in cldrPluralSamples) {
        val locale = Locale.forLanguageTagOrNull(sample.tag) ?: continue
        if (locale !in supportedLocales) continue
        val value = Decimal.parseOrNull(sample.value) ?: continue
        val type = if (sample.isOrdinal) PluralType.ORDINAL else PluralType.CARDINAL
        assertEquals(
            sample.category,
            pluralCategory(value, value.scale, locale, type).cldrName,
            "${sample.tag} ${if (sample.isOrdinal) "ordinal" else "cardinal"} ${sample.value}",
        )
        checked++
    }
    assertTrue(checked > 1000, "expected to check the full sample set, checked only $checked")
}

/**
 * Runs a number source through the shape checks every implementation owes,
 * regardless of where its data came from.
 */
public fun NumberFormatSource.assertNumbersAreWellShaped() {
    val english = Locale.of("en")
    assertTrue(supportedLocales.isNotEmpty(), "a CLDR-backed source is expected to enumerate its locales")

    for (locale in supportedLocales) {
        val symbols = symbols(locale)
        assertEquals(10, symbols.digits.size, "$locale does not carry ten digits")
        assertTrue(symbols.decimal.isNotEmpty(), "$locale has no decimal separator")
        assertTrue(symbols.minimumGroupingDigits >= 1, "$locale has an impossible minimumGroupingDigits")
        assertTrue(format(1234567L, locale).isNotBlank(), "$locale formatted nothing")
    }

    // A number round trips through its own locale, which is what makes the
    // separators the source hands out the same ones it prints.
    for (locale in listOf(english, Locale.of("de"), Locale.of("cs"), Locale.of("pl"))) {
        val original = Decimal.parse("1234.56")
        val formatted = format(original, locale, minimumFractionDigits = 2, maximumFractionDigits = 2)
        val parsed = assertNotNull(parseDecimalOrNull(formatted, locale), "$locale could not read back '$formatted'")
        assertEquals(0, original.compareTo(parsed), "$locale round trip: '$formatted' read back as $parsed")
    }
}

/**
 * Holds a number source to ICU's answers for the same questions.
 *
 * The CLDR sample fixture above proves the plural evaluator. This one proves the
 * rest of the engine, and it exists because several of the answers it checks are
 * not in any specification. UTS #35 says a compact pattern's significant digits
 * are "typically" two or three and then that an API may override; it does not
 * say to re-select the magnitude after rounding, so that 999999 is `1M` rather
 * than `1000K`; and it says nothing at all about the rounding mode. This library
 * picked half to even and ICU's compact default, and picking is only defensible
 * if something fails when the pick silently changes.
 *
 * The goldens are generated by calling ICU4J, not by reading its tables, and
 * locales whose symbols or patterns moved between ICU 78.3 and CLDR 48.2 are
 * left out at generation. A locale whose compact table alone moved keeps every
 * other option set, so the coverage narrows by option rather than by locale.
 */
public fun NumberFormatSource.assertConformsToIcuNumbers() {
    assertTrue(icuNumberGoldenData.size >= 30, "expected the full golden set, got ${icuNumberGoldenData.size}")
    val values = icuNumberGoldenValues.map { Decimal.parse(it) }
    var checked = 0
    var compactChecked = 0

    for ((tag, sets) in icuNumberGoldenData) {
        val locale = Locale.forLanguageTagOrNull(tag) ?: continue
        if (locale !in supportedLocales) continue
        for ((setName, expected) in sets) {
            for ((index, value) in values.withIndex()) {
                val want = expected[index]
                val got = formatForGolden(setName, value, locale) ?: continue
                assertEquals(want, got, "$tag $setName ${icuNumberGoldenValues[index]}")
                checked++
                if (setName.startsWith("compact")) compactChecked++
            }
        }
    }
    assertTrue(checked > 10000, "expected to check the full golden set, checked only $checked")
    assertTrue(compactChecked > 1000, "expected the compact sets to be covered, checked only $compactChecked")
}

/**
 * One golden option set applied to this source.
 *
 * The names are the ICU configurations the generator used, so this is where the
 * two vocabularies are lined up. `null` means the set is one this function does
 * not model, which fails the build at the check below rather than passing
 * quietly.
 */
private fun NumberFormatSource.formatForGolden(setName: String, value: Decimal, locale: Locale): String? = when (setName) {
    "standard" -> format(value, locale)
    "grouping-never" -> format(value, locale, grouping = NumberGrouping.NEVER)
    "grouping-always" -> format(value, locale, grouping = NumberGrouping.ALWAYS)
    "fraction-0" -> format(value, locale, minimumFractionDigits = 0, maximumFractionDigits = 0)
    "fraction-2" -> format(value, locale, minimumFractionDigits = 2, maximumFractionDigits = 2)
    "fraction-1-4" -> format(value, locale, minimumFractionDigits = 1, maximumFractionDigits = 4)
    "sign-always" -> format(value, locale, signDisplay = SignDisplay.ALWAYS)
    "sign-never" -> format(value, locale, signDisplay = SignDisplay.NEVER)
    "sign-except-zero" -> format(value, locale, signDisplay = SignDisplay.EXCEPT_ZERO)
    "sign-negative" -> format(
        value,
        locale,
        signDisplay = SignDisplay.NEGATIVE,
        minimumFractionDigits = 0,
        maximumFractionDigits = 0,
    )
    // The generator asks ICU's legacy percent instance, which multiplies and
    // uses CLDR's percent pattern. That is the reading formatPercent takes.
    "percent" -> formatPercent(value, locale)
    "percent-fraction-2" -> formatPercent(value, locale, fractionDigits = 2)
    "compact-short" -> format(value, locale, notation = NumberNotation.COMPACT_SHORT)
    "compact-long" -> format(value, locale, notation = NumberNotation.COMPACT_LONG)
    "compact-short-sign-always" ->
        format(value, locale, notation = NumberNotation.COMPACT_SHORT, signDisplay = SignDisplay.ALWAYS)
    else -> error("golden option set '$setName' has no mapping onto NumberFormatSource")
}

/**
 * Holds a plural source to ICU's answers, independently of CLDR's samples.
 *
 * [assertConformsToCldrPluralSamples] and this one check the same evaluator from
 * two directions. The samples say what CLDR intended and are parsed by the same
 * code that parses the rules, so in principle a misreading of the file format
 * could satisfy both the rules and the samples. ICU compiled its copy from the
 * same source and has never seen this repository's parser, so agreeing with both
 * is a stronger statement than agreeing with either.
 *
 * The decimals carry their trailing zeros because that is the whole difficulty:
 * `1` and `1.0` are one quantity and two categories in Czech, and the operands
 * that tell them apart are counts of visible digits rather than of value.
 */
public fun PluralRuleSource.assertConformsToIcuPlurals() {
    assertTrue(icuPluralGoldenData.size > 100, "expected the full golden set, got ${icuPluralGoldenData.size}")
    var checked = 0

    for ((tag, expected) in icuPluralGoldenData) {
        val locale = Locale.forLanguageTagOrNull(tag) ?: continue
        if (locale !in supportedLocales) continue
        val (cardinal, ordinal) = expected
        for ((type, answers) in listOf(PluralType.CARDINAL to cardinal, PluralType.ORDINAL to ordinal)) {
            for ((index, value) in icuPluralGoldenIntegers.withIndex()) {
                assertEquals(
                    answers[index],
                    pluralCategory(Decimal.of(value), 0, locale, type).cldrName,
                    "$tag $type $value",
                )
                checked++
            }
            for ((index, text) in icuPluralGoldenDecimals.withIndex()) {
                val value = Decimal.parse(text)
                assertEquals(
                    answers[icuPluralGoldenIntegers.size + index],
                    pluralCategory(value, value.scale, locale, type).cldrName,
                    "$tag $type $text",
                )
                checked++
            }
        }
    }
    assertTrue(checked > 10000, "expected to check the full golden set, checked only $checked")
}
