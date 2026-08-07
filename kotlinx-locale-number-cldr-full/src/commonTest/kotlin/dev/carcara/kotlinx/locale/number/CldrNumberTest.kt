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

package dev.carcara.kotlinx.locale.number

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.number.cldr.CldrNumber
import dev.carcara.kotlinx.locale.number.cldr.numberFormat
import dev.carcara.kotlinx.locale.number.cldr.numberFormatPercent
import dev.carcara.kotlinx.locale.number.cldr.numberOrdinal
import dev.carcara.kotlinx.locale.number.cldr.numberParseOrNull
import dev.carcara.kotlinx.locale.number.cldr.numberSymbols
import dev.carcara.kotlinx.locale.number.cldr.pluralCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val EN = Locale.of("en")
private val DE = Locale.of("de")
private val CS = Locale.of("cs")
private val TR = Locale.of("tr")
private val PL = Locale.of("pl")

class CldrNumberTest {

    @Test
    fun groupsWithTheLocaleSeparators() {
        assertEquals("1,234,567", numberFormat(1234567L, EN))
        assertEquals("1.234.567", numberFormat(1234567L, DE))
        // Czech groups with a no-break space, which is the character CLDR
        // declares and not the ASCII one.
        assertEquals("1 234 567", numberFormat(1234567L, CS))
    }

    @Test
    fun honoursMinimumGroupingDigits() {
        // Polish declares minimumGroupingDigits 2, so a four-digit number does
        // not group and a five-digit one does.
        assertEquals("1000", numberFormat(1000L, PL))
        assertEquals("10 000", numberFormat(10000L, PL))
        assertEquals("1,000", numberFormat(1000L, EN))
    }

    @Test
    fun percentTakesAFractionAndCarriesTheLocalePlacement() {
        val eighth = Decimal.parse("0.125")
        // 12.5 rounds to 12 rather than 13: LDML says nothing about the rounding
        // mode, so this library follows ICU and rounds half to even, which is
        // also what java.text.DecimalFormat does. ECMA-402 rounds half away from
        // zero and would print 13%.
        assertEquals("12%", numberFormatPercent(eighth, EN))
        assertEquals("12.5%", numberFormatPercent(eighth, EN, fractionDigits = 1))
        // Czech and German put a no-break space before the sign.
        assertEquals("12,5 %", numberFormatPercent(eighth, CS, fractionDigits = 1))
        assertEquals("12,5 %", numberFormatPercent(eighth, DE, fractionDigits = 1))
        // Turkish puts the sign in front.
        assertEquals("%12,5", numberFormatPercent(eighth, TR, fractionDigits = 1))
    }

    @Test
    fun compactPinsItsDefaultPrecision() {
        // Two significant digits or none, whichever keeps more: the value this
        // library pins where UTS #35 says only "typically".
        assertEquals("1.2K", numberFormat(1200L, EN, notation = NumberNotation.COMPACT_SHORT))
        assertEquals("12K", numberFormat(12345L, EN, notation = NumberNotation.COMPACT_SHORT))
        assertEquals("123K", numberFormat(123456L, EN, notation = NumberNotation.COMPACT_SHORT))
        // Rounding crosses a bucket rather than printing 1000K.
        assertEquals("1M", numberFormat(999999L, EN, notation = NumberNotation.COMPACT_SHORT))
        assertEquals("1.2M", numberFormat(1200000L, EN, notation = NumberNotation.COMPACT_SHORT))
    }

    @Test
    fun compactLongUsesTheLocaleWording() {
        assertEquals("1.2 thousand", numberFormat(1200L, EN, notation = NumberNotation.COMPACT_LONG))
        assertTrue(numberFormat(1200L, DE, notation = NumberNotation.COMPACT_LONG).isNotEmpty())
    }

    @Test
    fun pluralCategoryReadsTheVisibleDigits() {
        // The Czech rule is `one: i = 1 and v = 0` and `many: v != 0`, so the
        // same numeric value falls into different categories depending on how it
        // is about to be printed.
        assertEquals(PluralCategory.ONE, pluralCategory(1L, CS))
        assertEquals(PluralCategory.MANY, pluralCategory(Decimal.parse("1.0"), 1, CS))
        assertEquals(PluralCategory.FEW, pluralCategory(3L, CS))
        assertEquals(PluralCategory.OTHER, pluralCategory(10L, CS))
        assertEquals(PluralCategory.ONE, pluralCategory(1L, EN))
        assertEquals(PluralCategory.OTHER, pluralCategory(2L, EN))
    }

    @Test
    fun ordinalsFollowTheRuleSetOrRootsFullStop() {
        assertEquals("1st", numberOrdinal(1L, EN))
        assertEquals("2nd", numberOrdinal(2L, EN))
        assertEquals("3rd", numberOrdinal(3L, EN))
        assertEquals("4th", numberOrdinal(4L, EN))
        assertEquals("11th", numberOrdinal(11L, EN))
        assertEquals("21st", numberOrdinal(21L, EN))
        // German, Czech and the rest inherit root's rule, which appends a full
        // stop. That is the correct form rather than a gap in the data.
        assertEquals("1.", numberOrdinal(1L, DE))
        assertEquals("2.", numberOrdinal(2L, CS))
    }

    @Test
    fun signDisplayPutsThePlusWhereTheLocaleDoes() {
        assertEquals("+42", numberFormat(42L, EN, signDisplay = SignDisplay.ALWAYS))
        assertEquals("-42", numberFormat(-42L, EN))
        assertEquals("42", numberFormat(42L, EN))
        // EXCEPT_ZERO is the one a transaction list wants: a sign on every
        // movement, nothing on a zero balance.
        assertEquals("+42", numberFormat(42L, EN, signDisplay = SignDisplay.EXCEPT_ZERO))
        assertEquals("0", numberFormat(0L, EN, signDisplay = SignDisplay.EXCEPT_ZERO))
        assertEquals("+0", numberFormat(0L, EN, signDisplay = SignDisplay.ALWAYS))
        assertEquals("42", numberFormat(42L, EN, signDisplay = SignDisplay.NEVER))
        assertEquals("42", numberFormat(-42L, EN, signDisplay = SignDisplay.NEVER))
    }

    @Test
    fun symbolsAreHandedOutWhole() {
        val czech = numberSymbols(CS)
        assertEquals(",", czech.decimal)
        assertEquals(" ", czech.group)
        assertEquals("%", czech.percentSign)
        assertEquals(listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), czech.digits)
        assertEquals(1, czech.minimumGroupingDigits)
        assertEquals(2, numberSymbols(PL).minimumGroupingDigits)
    }

    @Test
    fun parsingKeepsTheDigitsItWasGiven() {
        val parsed = numberParseOrNull("1.50", EN)
        assertEquals(Decimal.parse("1.50"), parsed)
        assertEquals(2, parsed?.scale, "the scale is what the plural rules read")
        assertEquals(Decimal.parse("1234.5"), numberParseOrNull("1.234,5", DE))
        assertEquals(null, numberParseOrNull("not a number", EN))
    }

    @Test
    fun everyLocaleAnswers() {
        var checked = 0
        for (locale in CldrNumber.supportedLocales) {
            val formatted = numberFormat(1234567L, locale)
            assertTrue(formatted.isNotBlank(), "$locale formatted nothing")
            checked++
        }
        assertTrue(checked > 1000, "expected the full locale set, got $checked")
    }
}
