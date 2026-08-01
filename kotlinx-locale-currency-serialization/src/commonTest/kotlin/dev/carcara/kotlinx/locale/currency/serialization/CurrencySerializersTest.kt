package dev.carcara.kotlinx.locale.currency.serialization

import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.active
import dev.carcara.kotlinx.locale.currency.code
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CurrencySerializersTest {

    @Test
    fun writesEachCodeInItsOwnForm() {
        assertEquals("\"USD\"", Json.encodeToString(CurrencyCodeSerializer, Currency.USD))
        assertEquals("840", Json.encodeToString(CurrencyNumericCodeSerializer, Currency.USD))
        assertEquals("\"USD\"", Json.encodeToString(CurrencyLenientCodeSerializer, Currency.USD))
    }

    @Test
    fun roundTripsEveryCurrencyThroughTheAlphabeticForms() {
        for (currency in Currency.entries) {
            val alphabetic = listOf<KSerializer<Currency>>(CurrencyCodeSerializer, CurrencyLenientCodeSerializer)
            for (serializer in alphabetic) {
                val encoded = Json.encodeToString(serializer, currency)
                assertEquals(currency, Json.decodeFromString(serializer, encoded), "${currency.code} via $encoded")
            }
        }
    }

    @Test
    fun roundTripsEveryActiveCurrencyIsoAssignedANumberTo() {
        // The active set, because ISO reuses a numeric code across generations of
        // the same currency: 191 is both the 1991 Croatian dinar and the kuna. A
        // number therefore identifies a code only among the currencies still in
        // use, which is what the numeric serializer's KDoc says.
        val numbered = Currency.active.filter { it.numericCode >= 0 }
        for (currency in numbered) {
            val encoded = Json.encodeToString(CurrencyNumericCodeSerializer, currency)
            assertEquals(currency, Json.decodeFromString(CurrencyNumericCodeSerializer, encoded), "${currency.code} via $encoded")
        }
    }

    @Test
    fun theNumericSerializerIsTotalAgainstTheCurrentData() {
        // Currency.numericCode is documented as -1 where ISO assigns no number,
        // and CurrencyNumericCodeSerializer throws rather than write a sentinel
        // that reads back as nothing. Nothing in the bundled data reaches that
        // guard today, which is the fact worth pinning: if a regenerated -types
        // ever carries an unnumbered currency, this fails and says so, and the
        // guard stops being unreachable.
        val unnumbered = Currency.active.filter { it.numericCode < 0 }
        assertEquals(
            emptyList(),
            unnumbered.map { it.code },
            "these currencies cannot be written as a numeric code",
        )
        for (currency in Currency.active) {
            Json.encodeToString(CurrencyNumericCodeSerializer, currency)
        }
    }

    @Test
    fun readsCodesInAnyCase() {
        assertEquals(Currency.USD, Json.decodeFromString(CurrencyCodeSerializer, "\"usd\""))
        assertEquals(Currency.EUR, Json.decodeFromString(CurrencyLenientCodeSerializer, "\"eUr\""))
    }

    @Test
    fun theLenientSerializerReadsBothSpellings() {
        assertEquals(Currency.USD, Json.decodeFromString(CurrencyLenientCodeSerializer, "\"USD\""))
        assertEquals(Currency.USD, Json.decodeFromString(CurrencyLenientCodeSerializer, "\"840\""))
        assertEquals(Currency.EUR, Json.decodeFromString(CurrencyLenientCodeSerializer, "\"978\""))
    }

    @Test
    fun theTwoCodeSpacesDoNotOverlap() {
        // Three letters against digits: a string belongs to exactly one space,
        // which is what lets one reader take both without guessing.
        for (currency in Currency.entries) {
            assertEquals(3, currency.code.length, currency.code)
            assertFalse(currency.code.all { it in '0'..'9' }, currency.code)
        }
    }

    @Test
    fun theLenientSerializerAlwaysWritesTheAlphabeticCode() {
        val numbered = Currency.active.filter { it.numericCode >= 0 }
        for (currency in numbered) {
            val fromNumeric = Json.decodeFromString(CurrencyLenientCodeSerializer, "\"${currency.numericCode}\"")
            assertEquals("\"${currency.code}\"", Json.encodeToString(CurrencyLenientCodeSerializer, fromNumeric))
        }
    }

    @Test
    fun aJsonNumberNeedsLenientModeOrTheNumericSerializer() {
        assertFailsWith<SerializationException> { Json.decodeFromString(CurrencyLenientCodeSerializer, "840") }
        assertEquals(Currency.USD, Json.decodeFromString(CurrencyNumericCodeSerializer, "840"))

        val lenient = Json { isLenient = true }
        assertEquals(Currency.USD, lenient.decodeFromString(CurrencyLenientCodeSerializer, "840"))
    }

    @Test
    fun rejectsCodesOutsideTheActiveList() {
        assertFailsWith<SerializationException> { Json.decodeFromString(CurrencyCodeSerializer, "\"ZZZ\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(CurrencyNumericCodeSerializer, "1") }
        assertFailsWith<SerializationException> { Json.decodeFromString(CurrencyLenientCodeSerializer, "\"ZZZ\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(CurrencyLenientCodeSerializer, "\"\"") }
    }

    @Test
    fun theDefaultPluginEncodingIsAlreadyTheAlphabeticCode() {
        assertEquals("""{"settlement":"BRL"}""", Json.encodeToString(Account(Currency.BRL)))
    }

    @Test
    fun worksInsideAGeneratedSerializer() {
        val quote = Quote(Currency.EUR, Currency.JPY)
        val encoded = Json.encodeToString(quote)
        assertEquals("""{"base":"EUR","quote":392}""", encoded)
        assertEquals(quote, Json.decodeFromString<Quote>(encoded))
    }

    @Test
    fun worksAsACollectionElement() {
        val currencies = listOf(Currency.USD, Currency.EUR)
        val encoded = Json.encodeToString(ListSerializer(CurrencyCodeSerializer), currencies)
        assertEquals("""["USD","EUR"]""", encoded)
        assertEquals(currencies, Json.decodeFromString(ListSerializer(CurrencyCodeSerializer), encoded))
    }

    @Serializable
    private data class Account(val settlement: Currency)

    @Serializable
    private data class Quote(
        @Serializable(with = CurrencyCodeSerializer::class) val base: Currency,
        @Serializable(with = CurrencyNumericCodeSerializer::class) val quote: Currency,
    )
}
