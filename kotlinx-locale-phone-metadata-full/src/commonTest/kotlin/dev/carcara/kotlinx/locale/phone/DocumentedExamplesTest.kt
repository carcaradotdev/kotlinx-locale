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
import dev.carcara.kotlinx.locale.phone.metadata.PhoneNumbers
import dev.carcara.kotlinx.locale.phone.metadata.asYouType
import dev.carcara.kotlinx.locale.phone.metadata.format
import dev.carcara.kotlinx.locale.phone.metadata.isValid
import dev.carcara.kotlinx.locale.phone.metadata.phoneNumberOrNull
import dev.carcara.kotlinx.locale.phone.metadata.phoneRegionCandidates
import dev.carcara.kotlinx.locale.phone.metadata.phoneRegionOrNull
import dev.carcara.kotlinx.locale.phone.metadata.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every example written in API.md, run.
 *
 * Prose does not fail to compile, and the phone section is the newest and so the
 * most likely to drift. Each assertion here is one line of that document.
 */
class DocumentedExamplesTest {

    @Test
    fun theParseAndFormatExample() {
        val number = assertNotNull(phoneNumberOrNull("020 7123 4567", Country.GB))
        assertTrue(number.isValid())
        assertEquals(PhoneNumberType.FIXED_LINE, number.typeOf())
        assertEquals("+442071234567", number.format(PhoneNumberFormat.E164))
        assertEquals("020 7123 4567", number.format(PhoneNumberFormat.NATIONAL))
        assertEquals("+44 20 7123 4567", number.format(PhoneNumberFormat.INTERNATIONAL))
        assertEquals("tel:+44-20-7123-4567", number.format(PhoneNumberFormat.RFC3966))
        assertEquals(Country.GB, number.region)
        assertEquals("GB", number.regionCode)
    }

    @Test
    fun theAsYouTypeExample() {
        val formatter = Country.US.asYouType()
        assertEquals("2", formatter.append('2'))
        assertEquals("201-5", formatter.append("015"))
        assertEquals("(201) 555-0123", formatter.append("550123"))
        assertEquals("(201) 555-012", formatter.removeLast())
    }

    @Test
    fun theFailureReasonExample() {
        val result = PhoneNumbers.parse("1", Country.BR)
        assertTrue(result is PhoneParseResult.Failed)
        assertEquals(PhoneParseFailure.TOO_SHORT, result.reason)
    }

    @Test
    fun parsingAcceptsWhatPeopleType() {
        val forms = listOf(
            "+442071234567",
            "+44 20 7123 4567",
            "(020) 7123 4567",
            "020-7123-4567",
            "00442071234567",
        )
        for (text in forms) {
            val number = assertNotNull(phoneNumberOrNull(text, Country.GB), text)
            assertEquals("+442071234567", number.format(PhoneNumberFormat.E164), text)
        }
    }

    @Test
    fun theNumberCarriesItsOwnCountry() {
        // The whole point of the region property: one call, both facts.
        val number = assertNotNull(phoneNumberOrNull("+55 11 96123-4567"))
        assertEquals(Country.BR, number.region)
        assertEquals("+5511961234567", number.format(PhoneNumberFormat.E164))

        // And the same answer without ever building a number.
        assertEquals(Country.BR, phoneRegionOrNull("+55 11 96123-4567"))
        assertEquals(null, phoneRegionOrNull("11961234567"))
    }

    @Test
    fun aBareNationalNumberListsItsCandidates() {
        // No calling code means no country, so the honest answer is every
        // territory it would be valid in rather than a guess at one.
        val candidates = phoneRegionCandidates("2071234567")
        assertTrue(candidates.isNotEmpty(), "expected at least one candidate")
        assertTrue(Country.GB in candidates, "GB should be among $candidates")

        // A number that names itself needs no guessing.
        assertEquals(listOf(Country.GB), phoneRegionCandidates("+442071234567"))
    }

    @Test
    fun anExtensionSurvivesTheRoundTrip() {
        val number = assertNotNull(phoneNumberOrNull("+44 20 7123 4567 ext. 89"))
        assertEquals("89", number.extension)
        assertEquals("tel:+44-20-7123-4567;ext=89", number.format(PhoneNumberFormat.RFC3966))
    }
}
