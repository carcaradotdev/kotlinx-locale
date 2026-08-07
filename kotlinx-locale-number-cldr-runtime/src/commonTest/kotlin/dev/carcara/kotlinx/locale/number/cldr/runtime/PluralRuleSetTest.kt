@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.number.cldr.runtime

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.number.FormattedNumber
import dev.carcara.kotlinx.locale.number.PluralCategory
import dev.carcara.kotlinx.locale.test.assertEquals

/**
 * The plural rule evaluator, driven by hand-written CLDR conditions.
 *
 * `:conformance-icu` and the committed samples both check this against real
 * locales, which proves the shipped tables are read correctly and says nothing
 * about which operand a failure came from. These cases name the operand: each
 * one is a condition written out of UTS #35 and a value chosen to sit on one
 * side of it.
 *
 * That is the whole reason this module can now be tested at all. It takes a rule
 * set as a string in CLDR's own syntax, so a test needs no locale, no table and
 * no fixture.
 */
val PluralRuleSetTest by matrixSuite {

    /** A value as the formatter would hand it over: printed digits, not a number. */
    fun number(integerDigits: String, fractionDigits: String = "", compactExponent: Int = 0) = FormattedNumber(
        text = if (fractionDigits.isEmpty()) integerDigits else "$integerDigits.$fractionDigits",
        integerDigits = integerDigits,
        fractionDigits = fractionDigits,
        compactExponent = compactExponent,
    )

    test("a set with no rules is other") {
        assertEquals(PluralCategory.OTHER, PluralRuleSet.OtherOnly.select(number("1")))
        assertEquals(PluralCategory.OTHER, PluralRuleSet.parse("").select(number("1")))
    }

    test("English: one is exactly one, and only without fraction digits") {
        // en's real rule. `i = 1 and v = 0` is why 1.0 is `other` and 1 is `one`,
        // which is the operand distinction a numeric-only evaluator loses.
        val rules = PluralRuleSet.parse("one:i = 1 and v = 0")
        assertEquals(PluralCategory.ONE, rules.select(number("1")))
        assertEquals(PluralCategory.OTHER, rules.select(number("1", "0")))
        assertEquals(PluralCategory.OTHER, rules.select(number("2")))
        assertEquals(PluralCategory.OTHER, rules.select(number("0")))
    }

    test("Czech: few and many split on the same value by visible digits") {
        // The case the whole operand model exists for. 2 is `few`, 2.0 is `many`,
        // and the quantity is identical.
        val rules = PluralRuleSet.parse("one:i = 1 and v = 0;few:i = 2..4 and v = 0;many:v != 0")
        assertEquals(PluralCategory.ONE, rules.select(number("1")))
        assertEquals(PluralCategory.FEW, rules.select(number("2")))
        assertEquals(PluralCategory.FEW, rules.select(number("4")))
        assertEquals(PluralCategory.MANY, rules.select(number("2", "0")))
        assertEquals(PluralCategory.MANY, rules.select(number("1", "5")))
        assertEquals(PluralCategory.OTHER, rules.select(number("5")))
    }

    test("modulus conditions and ranges, which the Slavic rules are built from") {
        // ru's `one`: ends in 1 but not 11.
        val rules = PluralRuleSet.parse("one:v = 0 and i % 10 = 1 and i % 100 != 11")
        assertEquals(PluralCategory.ONE, rules.select(number("1")))
        assertEquals(PluralCategory.ONE, rules.select(number("21")))
        assertEquals(PluralCategory.ONE, rules.select(number("101")))
        assertEquals(PluralCategory.OTHER, rules.select(number("11")))
        assertEquals(PluralCategory.OTHER, rules.select(number("111")))
        assertEquals(PluralCategory.OTHER, rules.select(number("2")))
    }

    test("a comma in a condition is a set, and .. inside it is a range") {
        val rules = PluralRuleSet.parse("few:i % 10 = 2..4 and i % 100 != 12..14")
        assertEquals(PluralCategory.FEW, rules.select(number("22")))
        assertEquals(PluralCategory.FEW, rules.select(number("23")))
        assertEquals(PluralCategory.OTHER, rules.select(number("12")))
        assertEquals(PluralCategory.OTHER, rules.select(number("13")))
        assertEquals(PluralCategory.OTHER, rules.select(number("25")))
    }

    test("the first matching rule wins") {
        // CLDR orders its categories and the evaluator must not reorder them: a
        // value matching both `one` and `few` is whichever came first.
        val rules = PluralRuleSet.parse("one:i = 1;few:i = 1..4")
        assertEquals(PluralCategory.ONE, rules.select(number("1")))
        assertEquals(PluralCategory.FEW, rules.select(number("2")))
    }

    test("the f and t operands see the fraction digits themselves") {
        // f keeps trailing zeros and t drops them, which is what separates 1.50
        // from 1.5 in the locales that care.
        val rules = PluralRuleSet.parse("one:f = 5;few:t = 5")
        assertEquals(PluralCategory.ONE, rules.select(number("1", "5")))
        assertEquals(PluralCategory.FEW, rules.select(number("1", "50")))
    }
}
