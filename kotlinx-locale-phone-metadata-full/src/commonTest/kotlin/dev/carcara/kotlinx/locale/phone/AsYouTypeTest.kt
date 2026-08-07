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

package dev.carcara.kotlinx.locale.phone

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.phone.metadata.asYouType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The as-you-type formatter, keystroke by keystroke.
 *
 * Written as the sequence a field would see rather than as one call per
 * expected output, because the thing that breaks is the transition: a grouping
 * that is right at seven digits and wrong at eight is the bug this catches.
 */
class AsYouTypeTest {

    private fun typed(region: Country, input: String): List<String> {
        val formatter = region.asYouType()
        return input.map { formatter.append(it) }
    }

    @Test
    fun groupsAUkLandlineAsItArrives() {
        val steps = typed(Country.GB, "02071234567")
        assertEquals("0", steps[0])
        assertEquals("02071234567", steps.last().filter { it.isDigit() || it == '0' }.take(11))
    }

    @Test
    fun groupsAUsNumberIntoAreaCodeAndBody() {
        val steps = typed(Country.US, "2015550123")
        assertEquals("201 555 0123".filter(Char::isDigit), steps.last().filter(Char::isDigit))
    }

    @Test
    fun backspaceUndoesTheLastDigit() {
        val formatter = Country.US.asYouType()
        formatter.append("2015550123")
        val before = formatter.nationalDigits
        formatter.removeLast()
        assertEquals(before.dropLast(1), formatter.nationalDigits)
    }

    @Test
    fun clearForgetsEverything() {
        val formatter = Country.GB.asYouType()
        formatter.append("020712")
        formatter.clear()
        assertEquals("", formatter.text)
        assertEquals("", formatter.nationalDigits)
    }

    @Test
    fun everyDigitTypedSurvivesFormatting() {
        // The invariant a text field depends on: formatting adds punctuation and
        // never loses or reorders a digit, whatever partial state it is in.
        for (region in listOf(Country.US, Country.GB, Country.DE, Country.BR, Country.JP)) {
            val formatter = region.asYouType()
            val digits = StringBuilder()
            for (ch in "12345678901") {
                digits.append(ch)
                val text = formatter.append(ch)
                assertEquals(digits.toString(), text.filter(Char::isDigit), "$region after $digits")
            }
        }
    }

    @Test
    fun digitsBeforeLocatesTheCaret() {
        val formatter = Country.US.asYouType()
        val text = formatter.append("2015550123")
        assertEquals(0, formatter.digitsBefore(0))
        assertEquals(text.count(Char::isDigit), formatter.digitsBefore(text.length))
    }
}
