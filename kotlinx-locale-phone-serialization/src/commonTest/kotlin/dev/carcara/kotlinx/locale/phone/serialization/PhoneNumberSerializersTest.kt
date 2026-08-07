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

package dev.carcara.kotlinx.locale.phone.serialization

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.phone.PhoneNumber
import dev.carcara.kotlinx.locale.phone.metadata.PhoneNumbers
import dev.carcara.kotlinx.locale.phone.metadata.phoneNumberOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

private object E164 : PhoneNumberE164Serializer(PhoneNumbers)
private object International : PhoneNumberInternationalSerializer(PhoneNumbers)
private object National : PhoneNumberNationalSerializer(PhoneNumbers, Country.GB)
private object Rfc3966 : PhoneNumberRfc3966Serializer(PhoneNumbers)
private object Lenient : LenientPhoneNumberSerializer(PhoneNumbers, Country.GB)

class PhoneNumberSerializersTest {

    private val number = assertNotNull(phoneNumberOrNull("+44 20 7123 4567"))

    private fun encode(serializer: kotlinx.serialization.KSerializer<PhoneNumber>) = Json.encodeToString(serializer, number)

    @Test
    fun eachFormWritesItsOwnSpelling() {
        assertEquals("\"+442071234567\"", encode(E164))
        assertEquals("\"+44 20 7123 4567\"", encode(International))
        assertEquals("\"020 7123 4567\"", encode(National))
        assertEquals("\"tel:+44-20-7123-4567\"", encode(Rfc3966))
    }

    @Test
    fun eachFormReadsBackWhatItWrote() {
        for (serializer in listOf<kotlinx.serialization.KSerializer<PhoneNumber>>(E164, International, National, Rfc3966)) {
            assertEquals(number, Json.decodeFromString(serializer, encode(serializer)), serializer.descriptor.serialName)
        }
    }

    @Test
    fun theLenientOneReadsEveryFormAndWritesE164() {
        val spellings = listOf(
            "\"+442071234567\"",
            "\"+44 20 7123 4567\"",
            "\"020 7123 4567\"",
            "\"tel:+44-20-7123-4567\"",
            // And the shapes a human types, which the parser already accepts.
            "\"(020) 7123 4567\"",
            "\"00442071234567\"",
        )
        for (spelling in spellings) {
            assertEquals(number, Json.decodeFromString(Lenient, spelling), spelling)
        }
        // Asymmetric on purpose: reading is where the mess is.
        assertEquals("\"+442071234567\"", Json.encodeToString(Lenient, number))
    }

    @Test
    fun thePartsFormNeedsNoMetadata() {
        val encoded = Json.encodeToString(PhoneNumberPartsSerializer, number)
        assertEquals("""{"callingCode":44,"nationalNumber":"2071234567"}""", encoded)
        val decoded = Json.decodeFromString(PhoneNumberPartsSerializer, encoded)
        assertEquals(number, decoded)
    }

    @Test
    fun thePartsFormKeepsALeadingZero() {
        // The reason the national number is a string: 0212345678 is a Rome
        // landline and 212345678 is a different telephone.
        val rome = assertNotNull(phoneNumberOrNull("+39 02 1234 5678"))
        val encoded = Json.encodeToString(PhoneNumberPartsSerializer, rome)
        assertEquals("""{"callingCode":39,"nationalNumber":"0212345678"}""", encoded)
        assertEquals(rome, Json.decodeFromString(PhoneNumberPartsSerializer, encoded))
    }

    @Test
    fun anExtensionSurvivesTheFormsThatCarryOne() {
        val withExtension = assertNotNull(phoneNumberOrNull("+44 20 7123 4567 ext. 89"))
        assertEquals("89", withExtension.extension)
        for (serializer in listOf<kotlinx.serialization.KSerializer<PhoneNumber>>(Rfc3966, PhoneNumberPartsSerializer)) {
            val encoded = Json.encodeToString(serializer, withExtension)
            assertEquals("89", Json.decodeFromString(serializer, encoded).extension, serializer.descriptor.serialName)
        }
    }

    @Test
    fun rubbishIsRejectedRatherThanGuessedAt() {
        assertFailsWith<SerializationException> { Json.decodeFromString(E164, "\"not a number\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(Lenient, "\"\"") }
        assertFailsWith<SerializationException> {
            Json.decodeFromString(PhoneNumberPartsSerializer, """{"callingCode":44,"nationalNumber":"20x1234"}""")
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString(PhoneNumberPartsSerializer, """{"callingCode":44}""")
        }
    }

    @Test
    fun aNationalFormIsReadAgainstItsDeclaredRegion() {
        // The risk the KDoc names: the same national digits are a different
        // telephone in a different country, and only the declared region says
        // which one was meant.
        val underFrance = object : PhoneNumberNationalSerializer(PhoneNumbers, Country.FR) {}
        val french = assertNotNull(phoneNumberOrNull("+33 1 42 68 53 00"))
        assertEquals(french, Json.decodeFromString(underFrance, Json.encodeToString(underFrance, french)))
    }
}
