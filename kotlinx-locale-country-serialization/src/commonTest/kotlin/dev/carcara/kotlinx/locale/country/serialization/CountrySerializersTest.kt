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

package dev.carcara.kotlinx.locale.country.serialization

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.alpha2
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CountrySerializersTest {

    @Test
    fun writesEachCodeInItsOwnForm() {
        assertEquals("\"US\"", Json.encodeToString(CountryAlpha2Serializer, Country.US))
        assertEquals("\"USA\"", Json.encodeToString(CountryAlpha3Serializer, Country.US))
        assertEquals("840", Json.encodeToString(CountryNumericCodeSerializer, Country.US))
        assertEquals("\"US\"", Json.encodeToString(CountryLenientCodeSerializer, Country.US))
    }

    @Test
    fun roundTripsEveryCountryThroughEverySerializer() {
        for (country in Country.entries) {
            val textual = listOf<KSerializer<Country>>(CountryAlpha2Serializer, CountryAlpha3Serializer, CountryLenientCodeSerializer)
            for (serializer in textual) {
                val encoded = Json.encodeToString(serializer, country)
                assertEquals(country, Json.decodeFromString(serializer, encoded), "${country.alpha2} via $encoded")
            }
            val numeric = Json.encodeToString(CountryNumericCodeSerializer, country)
            assertEquals(country, Json.decodeFromString(CountryNumericCodeSerializer, numeric), "${country.alpha2} via $numeric")
        }
    }

    @Test
    fun readsCodesInAnyCase() {
        assertEquals(Country.US, Json.decodeFromString(CountryAlpha2Serializer, "\"us\""))
        assertEquals(Country.US, Json.decodeFromString(CountryAlpha3Serializer, "\"usa\""))
        assertEquals(Country.US, Json.decodeFromString(CountryLenientCodeSerializer, "\"uSa\""))
    }

    @Test
    fun theNamedSerializersRejectTheOtherForms() {
        // Which is why they are worth naming: a field declared alpha-3 fails
        // loudly on the day a producer starts sending alpha-2.
        assertFailsWith<SerializationException> { Json.decodeFromString(CountryAlpha3Serializer, "\"US\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(CountryAlpha2Serializer, "\"USA\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(CountryAlpha2Serializer, "\"840\"") }
    }

    @Test
    fun theLenientSerializerReadsAllThreeSpellings() {
        for (text in listOf("\"US\"", "\"USA\"", "\"840\"")) {
            assertEquals(Country.US, Json.decodeFromString(CountryLenientCodeSerializer, text), text)
        }
        // Zero-padded numeric codes are the printed form of the standard, so a
        // producer that pads is reading the standard rather than getting it wrong.
        assertEquals(Country.AD, Json.decodeFromString(CountryLenientCodeSerializer, "\"020\""))
        assertEquals(Country.AD, Json.decodeFromString(CountryLenientCodeSerializer, "\"20\""))
        assertEquals(Country.AF, Json.decodeFromString(CountryLenientCodeSerializer, "\"004\""))
    }

    @Test
    fun theThreeCodeSpacesDoNotOverlap() {
        // What makes one lenient reader possible at all: no alpha-2 is also an
        // alpha-3, and no alphabetic code is all digits, so a string belongs to
        // exactly one space and the reader never has to guess.
        val alpha2 = Country.entries.map { it.alpha2 }.toSet()
        val alpha3 = Country.entries.map { it.alpha3 }.toSet()
        assertEquals(emptySet<String>(), alpha2 intersect alpha3)
        for (country in Country.entries) {
            assertEquals(2, country.alpha2.length)
            assertEquals(3, country.alpha3.length)
        }
    }

    @Test
    fun theLenientSerializerAlwaysWritesAlphaTwo() {
        // Reading many forms and writing one is what makes it safe to migrate a
        // field: the second time a row is written it is in the canonical form.
        for (country in Country.entries) {
            val fromAlpha3 = Json.decodeFromString(CountryLenientCodeSerializer, "\"${country.alpha3}\"")
            assertEquals("\"${country.alpha2}\"", Json.encodeToString(CountryLenientCodeSerializer, fromAlpha3))
        }
    }

    @Test
    fun aJsonNumberNeedsLenientModeOrTheNumericSerializer() {
        // The documented consequence of a Decoder having to commit to
        // decodeString before it can see what is coming.
        assertFailsWith<SerializationException> { Json.decodeFromString(CountryLenientCodeSerializer, "840") }
        assertEquals(Country.US, Json.decodeFromString(CountryNumericCodeSerializer, "840"))

        val lenient = Json { isLenient = true }
        assertEquals(Country.US, lenient.decodeFromString(CountryLenientCodeSerializer, "840"))
    }

    @Test
    fun rejectsUnassignedCodes() {
        assertFailsWith<SerializationException> { Json.decodeFromString(CountryAlpha2Serializer, "\"ZZ\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(CountryAlpha3Serializer, "\"ZZZ\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(CountryNumericCodeSerializer, "999") }
        assertFailsWith<SerializationException> { Json.decodeFromString(CountryLenientCodeSerializer, "\"ZZ\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(CountryLenientCodeSerializer, "\"UNITED\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(CountryLenientCodeSerializer, "\"\"") }
    }

    @Test
    fun theDefaultPluginEncodingIsAlreadyAlphaTwo() {
        // Nothing here changes what a Country property serializes to without an
        // annotation: the entry names are the alpha-2 codes. Naming
        // CountryAlpha2Serializer states the contract, it does not impose it.
        assertEquals("""{"origin":"BR"}""", Json.encodeToString(Shipment(Country.BR)))
    }

    @Test
    fun worksInsideAGeneratedSerializer() {
        val route = Route(Country.BR, Country.DE, Country.JP)
        val encoded = Json.encodeToString(route)
        assertEquals("""{"from":"BR","via":"DEU","to":392}""", encoded)
        assertEquals(route, Json.decodeFromString<Route>(encoded))
    }

    @Test
    fun worksAsACollectionElement() {
        val countries = listOf(Country.BR, Country.DE)
        val encoded = Json.encodeToString(ListSerializer(CountryAlpha3Serializer), countries)
        assertEquals("""["BRA","DEU"]""", encoded)
        assertEquals(countries, Json.decodeFromString(ListSerializer(CountryAlpha3Serializer), encoded))
    }

    @Serializable
    private data class Shipment(val origin: Country)

    @Serializable
    private data class Route(
        @Serializable(with = CountryAlpha2Serializer::class) val from: Country,
        @Serializable(with = CountryAlpha3Serializer::class) val via: Country,
        @Serializable(with = CountryNumericCodeSerializer::class) val to: Country,
    )
}
