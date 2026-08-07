@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.number.cldr.runtime

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNull

/**
 * The pattern parser, driven by hand-written UTS #35 patterns.
 *
 * This module shipped with no tests at all, which is the wrong way round: it
 * holds the parser every number in the library is rendered through, and it was
 * reachable only via the eleven-hundred-locale table in `-cldr-full`. A failure
 * there says "pt-BR formats wrongly" when what broke was grouping-size parsing.
 *
 * `NumberPattern.parse` takes CLDR's own syntax, so a case here is a line a
 * reader can check against the specification without decoding anything.
 */
val NumberPatternTest by matrixSuite {

    test("reads the plain decimal pattern every locale starts from") {
        val pattern = NumberPattern.parse("#,##0.###")
        assertEquals("", pattern.positivePrefix)
        assertEquals("", pattern.positiveSuffix)
        assertEquals(3, pattern.primaryGroupSize)
        assertEquals(3, pattern.secondaryGroupSize)
        assertEquals(1, pattern.minimumIntegerDigits)
        assertEquals(0, pattern.minimumFractionDigits)
        assertEquals(3, pattern.maximumFractionDigits)
        assertEquals(1, pattern.multiplier)
    }

    test("a second group is the Indian pattern, and it is not the first") {
        // #,##,##0 groups the low three digits and then twos. Reading both sizes
        // as three renders 1234567 as 1,234,567 in a locale that writes
        // 12,34,567, and every digit is still correct, which is what makes this
        // the kind of bug a golden catches late and a parser test catches now.
        val pattern = NumberPattern.parse("#,##,##0.###")
        assertEquals(3, pattern.primaryGroupSize)
        assertEquals(2, pattern.secondaryGroupSize)
    }

    test("no comma means no grouping") {
        val pattern = NumberPattern.parse("0.######")
        assertEquals(0, pattern.primaryGroupSize)
    }

    test("percent and per mille carry their multiplier") {
        assertEquals(100, NumberPattern.parse("#,##0%").multiplier)
        assertEquals(1000, NumberPattern.parse("#,##0‰").multiplier)
        assertEquals(1, NumberPattern.parse("#,##0").multiplier)
    }

    test("a quoted percent is a literal, not a multiplier") {
        // CLDR quotes a percent sign that is meant to be printed. Treating it as
        // a multiplier would silently scale the value by a hundred.
        assertEquals(1, NumberPattern.parse("#,##0'%'").multiplier)
    }

    test("fraction digits come from the zeros and the hashes") {
        val pattern = NumberPattern.parse("#,##0.00##")
        assertEquals(2, pattern.minimumFractionDigits)
        assertEquals(4, pattern.maximumFractionDigits)
    }

    test("leading zeros are the minimum integer digits") {
        assertEquals(3, NumberPattern.parse("000").minimumIntegerDigits)
        assertEquals(1, NumberPattern.parse("#0").minimumIntegerDigits)
    }

    test("affixes are kept on both sides and both signs") {
        val pattern = NumberPattern.parse("¤#,##0.00;(¤#,##0.00)")
        assertEquals("¤", pattern.positivePrefix)
        assertEquals("", pattern.positiveSuffix)
        assertEquals("(¤", pattern.negativePrefix)
        assertEquals(")", pattern.negativeSuffix)
    }

    test("one subpattern leaves the negative form unstated") {
        // Absent rather than empty: a renderer that reads null knows to build the
        // negative form from the positive one and the minus sign, and a renderer
        // that reads "" would print no sign at all.
        val pattern = NumberPattern.parse("#,##0.00")
        assertNull(pattern.negativePrefix)
        assertNull(pattern.negativeSuffix)
    }

    test("significant digits in the pattern are a rounding increment") {
        // The rare form CLDR uses for the currencies that round to a nickel.
        assertEquals(5L, NumberPattern.parse("#,##0.05").roundingIncrement)
        assertEquals(0L, NumberPattern.parse("#,##0.00").roundingIncrement)
    }
}
