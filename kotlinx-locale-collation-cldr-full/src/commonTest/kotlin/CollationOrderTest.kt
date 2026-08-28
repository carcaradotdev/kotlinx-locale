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

package dev.carcara.kotlinx.locale.collation.cldr

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.collation.CollationStrength
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * The order every target has to agree on, asserted on the shipped tables.
 *
 * These run on all of them rather than on the JVM alone, because the packing
 * differs per target and a table that unpacks wrongly is a table that sorts
 * wrongly. The cases are letters a locale actually tailors, not a generic word
 * list: a diacritic fold would pass a generic list and fail every one of these.
 */
val CollationOrderTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("accentedInitialsSortUnderTheirOwnLetter") {
        val de = Locale.forLanguageTag("de")
        assertEquals(
            listOf("Ísland", "Österreich", "Zypern"),
            listOf("Zypern", "Ísland", "Österreich").sortedWith(collationComparator(de)),
        )
    }

    test("czechSortsCaronsAndTheChDigraphAsLetters") {
        val cs = Locale.forLanguageTag("cs")
        // č is its own letter after c, and ch is one letter after h.
        assertEquals(
            listOf("Cyprus", "Česko", "Dánsko", "Chorvatsko"),
            listOf("Chorvatsko", "Česko", "Cyprus", "Dánsko").sortedWith(collationComparator(cs)),
        )
    }

    test("icelandicEndsItsAlphabetWithThorn") {
        val locale = Locale.forLanguageTag("is")
        assertEquals(
            listOf("Albanía", "Írland", "Ísland", "Ítalía", "Japan", "Þýskaland"),
            listOf("Þýskaland", "Ísland", "Japan", "Albanía", "Ítalía", "Írland")
                .sortedWith(collationComparator(locale)),
        )
    }

    test("hungarianReadsCsAndZsAsSingleLetters") {
        val hu = Locale.forLanguageTag("hu")
        assertEquals(
            listOf("Ciprus", "Csád", "Zambia", "Zsombó"),
            listOf("Zsombó", "Csád", "Ciprus", "Zambia").sortedWith(collationComparator(hu)),
        )
    }

    test("scriptReorderingLiftsTheLocalesOwnAlphabet") {
        // [reorder Cyrl]: in Russian the Cyrillic block sorts above Latin, and in
        // English it does not. Same two strings, opposite answers.
        val ru = Locale.forLanguageTag("ru")
        val en = Locale.forLanguageTag("en")
        assertTrue(collationComparator(ru).compare("Ель", "Elm") < 0)
        assertTrue(collationComparator(en).compare("Ель", "Elm") > 0)
    }

    test("strengthDecidesHowMuchOfADifferenceCounts") {
        val en = Locale.forLanguageTag("en")
        val primary = collationComparator(en, CollationStrength.PRIMARY)
        val secondary = collationComparator(en, CollationStrength.SECONDARY)
        val tertiary = collationComparator(en, CollationStrength.TERTIARY)

        // Base letters only: the accent and the case both stop counting.
        assertEquals(0, primary.compare("resume", "résumé"))
        assertEquals(0, primary.compare("a", "A"))
        // Accents count, case does not.
        assertTrue(secondary.compare("resume", "résumé") < 0)
        assertEquals(0, secondary.compare("a", "A"))
        // Everything counts, which is what orders a list.
        assertTrue(tertiary.compare("resume", "résumé") < 0)
        assertTrue(tertiary.compare("a", "A") < 0)
    }

    test("normalisationMakesTheTwoSpellingsOneWord") {
        val en = Locale.forLanguageTag("en")
        // A with combining diaeresis, against the single code point.
        assertEquals(0, collationComparator(en).compare("Ä", "Ä"))
    }

    test("anUntailoredLocaleStillSortsInRootOrder") {
        // No CLDR tailoring for this one, so it reads the root table. Root order
        // is still an order: it is code point order that is not.
        val locale = Locale.forLanguageTag("en-NZ")
        assertTrue(collationComparator(locale).compare("apple", "Banana") < 0)
    }

    test("hanSortsByRadicalStrokeRatherThanCodePoint") {
        val en = Locale.forLanguageTag("en")
        // U+4E00 has the lower code point, and the radical-stroke order the root
        // table carries agrees with it here. U+9FFF is the last ideograph in the
        // block and sorts after both.
        val sorted = listOf("龘", "一", "二").sortedWith(collationComparator(en))
        assertEquals("一", sorted.first())
    }
}
