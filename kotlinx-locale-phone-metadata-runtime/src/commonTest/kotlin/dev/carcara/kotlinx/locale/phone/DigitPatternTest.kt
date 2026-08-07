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

import dev.carcara.kotlinx.locale.phone.metadata.runtime.DigitPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The matcher is the piece the whole domain rests on, so it is tested against
 * the constructs the metadata uses rather than only through the metadata.
 */
class DigitPatternTest {

    @Test
    fun matchesLiteralsAndDigitClasses() {
        assertTrue(DigitPattern.parse("""\d{3}""").matches("123"))
        assertFalse(DigitPattern.parse("""\d{3}""").matches("12"))
        assertFalse(DigitPattern.parse("""\d{3}""").matches("1234"))
        assertTrue(DigitPattern.parse("[1-357-9]").matches("8"))
        assertFalse(DigitPattern.parse("[1-357-9]").matches("6"))
    }

    @Test
    fun matchesAlternationAndGroups() {
        val pattern = DigitPattern.parse("""(?:1|22|333)\d{2}""")
        assertTrue(pattern.matches("145"))
        assertTrue(pattern.matches("2245"))
        assertTrue(pattern.matches("33345"))
        assertFalse(pattern.matches("445"))
    }

    @Test
    fun matchesBoundedRepetitionAndOptional() {
        val pattern = DigitPattern.parse("""8\d{2,4}""")
        assertFalse(pattern.matches("81"))
        assertTrue(pattern.matches("812"))
        assertTrue(pattern.matches("81234"))
        assertFalse(pattern.matches("812345"))
        assertTrue(DigitPattern.parse("""1?23""").matches("23"))
        assertTrue(DigitPattern.parse("""1?23""").matches("123"))
    }

    @Test
    fun capturesGroupsInOrder() {
        val pattern = DigitPattern.parse("""(\d{3})(\d{3})""")
        assertEquals(listOf("712", "345"), pattern.capture("712345"))
        assertNull(pattern.capture("71234"))
    }

    @Test
    fun capturesNestedAndOptionalGroups() {
        val pattern = DigitPattern.parse("""(\d{2})(?:(\d{2}))?(\d{2})""")
        assertEquals(listOf("12", "34", "56"), pattern.capture("123456"))
        assertEquals(listOf("12", null, "34"), pattern.capture("1234"))
    }

    @Test
    fun matchesPrefixesForNationalPrefixStripping() {
        // Antigua's rule: strip a seven-digit local number only at the end.
        val pattern = DigitPattern.parse("""([457]\d{6})$|1""")
        assertEquals(7, pattern.prefixLength("4123456"))
        assertEquals(1, pattern.prefixLength("1268464123"))
        assertEquals(-1, pattern.prefixLength("9123456"))
    }

    @Test
    fun endAnchorRefusesATrailingRemainder() {
        val pattern = DigitPattern.parse("""(\d{3})$""")
        assertEquals(3, pattern.prefixLength("123"))
        assertEquals(-1, pattern.prefixLength("1234"))
    }
}
