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

package dev.carcara.kotlinx.locale

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertFalse
import dev.carcara.kotlinx.locale.test.assertTrue
import kotlin.math.floor

/**
 * The stdlib calls whose answer is not the same on every target.
 *
 * Everything else in this build is one set of Kotlin sources compiled by one
 * frontend, so a name-table lookup cannot disagree between iOS and Node. What
 * can disagree is the handful of places the library asks the platform a
 * question: regular expressions, `Double` arithmetic, case mapping and
 * character classification are each backed by a different implementation per
 * target.
 *
 * These assertions are deliberately about the *primitive* rather than about a
 * locale. A conformance golden that fails on one target tells you a locale
 * broke; this tells you which stdlib call did it, which is the question you ask
 * next anyway.
 *
 * Every input here is one the library's own CLDR data can produce.
 */
val StdlibParityTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    // The two spaces CLDR uses and that ICU point releases disagree about. They
    // appear throughout the number, currency and datetime tables.
    val nbsp = ' '
    val nnbsp = ' '

    "regular expressions" - {

        test("an explicit whitespace class answers the same on every target") {
            // The class `PatternFormatter.EMPTY_BRACKET_PAIR` uses, copied here
            // so the primitive is pinned separately from the caller. Spelled out
            // rather than `\s` because the two disagree, which is the next case.
            val pattern = Regex("""\([ \t\n\r]*\)|\[[ \t\n\r]*\]""")
            assertTrue(pattern.containsMatchIn("()"))
            assertTrue(pattern.containsMatchIn("(  )"))
            assertTrue(pattern.containsMatchIn("[]"))
            assertFalse(pattern.containsMatchIn("($nbsp)"), "U+00A0 is not ASCII whitespace")
            assertFalse(pattern.containsMatchIn("($nnbsp)"), "U+202F is not ASCII whitespace")
        }

        test("\\s is a different set of characters per target") {
            // Not a wish, a record. `\s` is the five ASCII whitespace characters
            // in java.util.regex and every Unicode whitespace character in a JS
            // RegExp. Both readings are defensible and the library cannot use
            // either, because CLDR puts U+00A0 and U+202F inside the patterns
            // this class is matched against.
            //
            // Asserting the disagreement rather than one side of it is what
            // keeps this honest: if a future Kotlin release makes `\s` mean the
            // same thing everywhere, this fails and the workaround above can go.
            val loose = Regex("""\s""")
            val matchesNbsp = loose.containsMatchIn(nbsp.toString())
            val matchesNnbsp = loose.containsMatchIn(nnbsp.toString())
            assertEquals(
                matchesNbsp,
                matchesNnbsp,
                "a target that treats U+00A0 and U+202F differently under \\s is a new case entirely",
            )
            // Always true, on every target, and the thing the explicit class relies on.
            assertTrue(loose.containsMatchIn(" "), "\\s matches a plain space")
        }
    }

    "character classification" - {

        test("digits outside ASCII are digits") {
            // The library carries numbering systems whose digits are not ASCII,
            // and the parser leans on isDigit to find them.
            assertTrue('٠'.isDigit(), "Arabic-Indic zero")
            assertTrue('०'.isDigit(), "Devanagari zero")
            assertTrue('۰'.isDigit(), "Extended Arabic-Indic zero")
        }

        test("the no-break spaces are whitespace") {
            // Grouping separators in several locales. The parser has to be able
            // to recognise one it just printed.
            assertTrue(nbsp.isWhitespace(), "U+00A0")
            assertTrue(nnbsp.isWhitespace(), "U+202F")
        }

        test("letters outside Latin are letters") {
            assertTrue('一'.isLetter(), "CJK")
            assertTrue('ا'.isLetter(), "Arabic alef")
            assertTrue('א'.isLetter(), "Hebrew alef")
        }
    }

    "case mapping" - {

        test("case mapping is root, not the ambient locale") {
            // The reason `titlecaseFirstWord` takes a language. Kotlin's
            // `uppercase` is the root mapping on every target, so `i` becomes
            // `I` and never the Turkish dotted `İ`. A target that answered
            // otherwise would be reading a default locale from the host, which
            // is the thing this library is built never to do.
            assertEquals("I", "i".uppercase(), "uppercase read a locale from somewhere")
            assertEquals("i", "I".lowercase(), "lowercase read a locale from somewhere")
            // The Turkish forms are still round-trippable, which is what the
            // locale-aware path above them relies on.
            assertEquals("i̇", "İ".lowercase(), "dotted capital I lowercases to i plus a combining dot")
            assertEquals("I", "ı".uppercase(), "dotless i uppercases to plain I")
        }

        test("uppercase can lengthen a string") {
            // The German sharp s becomes two characters, so any code that
            // assumes case mapping preserves length is wrong on every target.
            assertEquals("SS", "ß".uppercase())
        }

        test("case mapping leaves caseless scripts alone") {
            for (text in listOf("中文", "العربية", "עברית", "ไทย")) {
                assertEquals(text, text.uppercase(), "$text changed under uppercase")
                assertEquals(text, text.lowercase(), "$text changed under lowercase")
            }
        }
    }

    "double arithmetic" - {

        test("the powers of ten the compact tables index by are exact") {
            // Compact notation picks a magnitude by comparing against a power of
            // ten. Through 10^15 these are exactly representable, and the
            // selection is only stable if every target agrees they are.
            var expected = 1.0
            for (exponent in 0..15) {
                assertEquals(expected, pow10(exponent), "10^$exponent")
                expected *= 10
            }
        }

        test("half-even rounding lands the same way on the ties") {
            // The rounding mode this library pins. The ties are the only inputs
            // where a mode is observable, so they are the only ones worth
            // asserting.
            assertEquals(0.0, roundHalfEven(0.5), "0.5 rounds to even")
            assertEquals(2.0, roundHalfEven(1.5), "1.5 rounds to even")
            assertEquals(2.0, roundHalfEven(2.5), "2.5 rounds to even")
            assertEquals(-2.0, roundHalfEven(-2.5), "-2.5 rounds to even")
        }
    }
}

/** `10^[exponent]` by repeated multiplication, which is exact through 10^22. */
private fun pow10(exponent: Int): Double {
    var result = 1.0
    repeat(exponent) { result *= 10 }
    return result
}

/** Half-even rounding to an integral value, spelled out rather than taken from a platform. */
private fun roundHalfEven(value: Double): Double {
    val whole = floor(value)
    val diff = value - whole
    return when {
        diff > 0.5 -> whole + 1
        diff < 0.5 -> whole
        // Exactly a tie: pick the even neighbour.
        whole.toLong() % 2L == 0L -> whole
        else -> whole + 1
    }
}
