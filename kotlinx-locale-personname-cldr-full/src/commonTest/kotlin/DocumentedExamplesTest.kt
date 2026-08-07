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

package dev.carcara.kotlinx.locale.personname.cldr

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.personname.PersonName
import dev.carcara.kotlinx.locale.personname.PersonNameLength
import dev.carcara.kotlinx.locale.personname.PersonNameOrder
import dev.carcara.kotlinx.locale.personname.PersonNameUsage
import dev.carcara.kotlinx.locale.test.assertEquals

/** Every person name example in API.md, asserted so the prose cannot drift. */
val DocumentedExamplesTest by matrixSuite {

    val name = PersonName(given = "Iris", surname = "Adler")

    test("theFormattingExamples") {
        val en = Locale.forLanguageTag("en")
        assertEquals("Iris Adler", personNameFormat(name, locale = en))
        // English defaults to a medium, informal monogram, which is one letter.
        assertEquals("I", personNameFormat(name, usage = PersonNameUsage.MONOGRAM, locale = en))
        assertEquals(
            "IA",
            personNameFormat(name, length = PersonNameLength.LONG, usage = PersonNameUsage.MONOGRAM, locale = en),
        )
        assertEquals("Adler, Iris", personNameFormat(name, order = PersonNameOrder.SORTING, locale = en))
    }

    test("theOrderExamples") {
        val hu = Locale.forLanguageTag("hu")
        val en = Locale.forLanguageTag("en")
        assertEquals(PersonNameOrder.SURNAME_FIRST, personNameOrder(hu, hu))
        assertEquals(PersonNameOrder.GIVEN_FIRST, personNameOrder(hu, en))
    }

    test("aOnePartNameIsWrittenOutRatherThanAbbreviated") {
        val en = Locale.forLanguageTag("en")
        assertEquals("Zendaya", personNameFormat(PersonName(given = "Zendaya"), locale = en))
    }
}
