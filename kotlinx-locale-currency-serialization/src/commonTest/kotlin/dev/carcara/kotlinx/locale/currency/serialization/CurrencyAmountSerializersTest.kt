package dev.carcara.kotlinx.locale.currency.serialization

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertFailsWith
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
private data class Invoice(
    @Serializable(with = CurrencyAmountMinorUnitsSerializer::class) val subtotal: CurrencyAmount,
    @Serializable(with = CurrencyAmountDecimalSerializer::class) val tax: CurrencyAmount,
    @Serializable(with = CurrencyAmountCodeAndDecimalSerializer::class) val total: CurrencyAmount,
)
val CurrencyAmountSerializersTest by matrixSuite {
    val forms = listOf<KSerializer<CurrencyAmount>>(
        CurrencyAmountMinorUnitsSerializer,
        CurrencyAmountDecimalSerializer,
        CurrencyAmountCodeAndDecimalSerializer,
    )

    test("writesEachFormAsDocumented") {
        val amount = CurrencyAmount(Currency.USD, 1234_56)
        assertEquals("""{"currency":"USD","minorUnits":123456}""", Json.encodeToString(CurrencyAmountMinorUnitsSerializer, amount))
        assertEquals("""{"currency":"USD","amount":"1234.56"}""", Json.encodeToString(CurrencyAmountDecimalSerializer, amount))
        assertEquals("\"USD 1234.56\"", Json.encodeToString(CurrencyAmountCodeAndDecimalSerializer, amount))
    }

    test("carriesTheFractionDigitsOfTheCurrency") {
        // JPY has none and BHD has three, so the same Long means a different
        // amount in each. The decimal forms are where that becomes visible.
        val yen = CurrencyAmount(Currency.JPY, 500)
        assertEquals("""{"currency":"JPY","amount":"500"}""", Json.encodeToString(CurrencyAmountDecimalSerializer, yen))
        assertEquals("\"JPY 500\"", Json.encodeToString(CurrencyAmountCodeAndDecimalSerializer, yen))

        val dinar = CurrencyAmount(Currency.BHD, 1234)
        assertEquals("""{"currency":"BHD","amount":"1.234"}""", Json.encodeToString(CurrencyAmountDecimalSerializer, dinar))
        assertEquals("\"BHD 1.234\"", Json.encodeToString(CurrencyAmountCodeAndDecimalSerializer, dinar))
    }

    test("roundTripsThroughEveryForm") {
        val amounts = listOf(
            CurrencyAmount(Currency.USD, 1234_56),
            CurrencyAmount(Currency.USD, -1250),
            CurrencyAmount(Currency.USD, 0),
            CurrencyAmount(Currency.JPY, 500),
            CurrencyAmount(Currency.BHD, 1234),
            CurrencyAmount(Currency.EUR, Long.MAX_VALUE),
            CurrencyAmount(Currency.EUR, Long.MIN_VALUE),
        )
        for (amount in amounts) {
            for (serializer in forms) {
                val encoded = Json.encodeToString(serializer, amount)
                assertEquals(amount, Json.decodeFromString(serializer, encoded), encoded)
            }
        }
    }

    test("keepsTheSignOnBothHalves") {
        val overdraft = CurrencyAmount(Currency.USD, -1250)
        assertEquals("""{"currency":"USD","amount":"-12.50"}""", Json.encodeToString(CurrencyAmountDecimalSerializer, overdraft))
        assertEquals("\"USD -12.50\"", Json.encodeToString(CurrencyAmountCodeAndDecimalSerializer, overdraft))
        assertEquals(overdraft, Json.decodeFromString(CurrencyAmountCodeAndDecimalSerializer, "\"USD -12.50\""))
    }

    test("readsTheObjectFormsInAnyFieldOrder") {
        // An amount cannot be parsed before its currency is known, so the
        // decimal form collects both fields before it builds anything.
        val amount = CurrencyAmount(Currency.USD, 1234_56)
        assertEquals(
            amount,
            Json.decodeFromString(CurrencyAmountDecimalSerializer, """{"amount":"1234.56","currency":"USD"}"""),
        )
        assertEquals(
            amount,
            Json.decodeFromString(CurrencyAmountMinorUnitsSerializer, """{"minorUnits":123456,"currency":"USD"}"""),
        )
    }

    test("rejectsAMissingField") {
        assertFailsWith<SerializationException> {
            Json.decodeFromString(CurrencyAmountMinorUnitsSerializer, """{"currency":"USD"}""")
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString(CurrencyAmountMinorUnitsSerializer, """{"minorUnits":1}""")
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString(CurrencyAmountDecimalSerializer, """{"currency":"USD"}""")
        }
    }

    test("rejectsMoreFractionDigitsThanTheCurrencyHas") {
        // Silently rounding a payment is the one behavior a money type must not
        // have, so the strictness of CurrencyAmount.parse is inherited whole.
        assertFailsWith<SerializationException> {
            Json.decodeFromString(CurrencyAmountDecimalSerializer, """{"currency":"USD","amount":"12.345"}""")
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString(CurrencyAmountCodeAndDecimalSerializer, "\"JPY 5.5\"")
        }
    }

    test("rejectsALocaleFormattedAmount") {
        // The reason none of this touches Locale: what a person reads is not
        // what round trips. Grouping separators and symbols do not parse.
        for (text in listOf("\"USD 1,234.56\"", "\"USD \$1234.56\"", "\"USD 1.234,56\"")) {
            assertFailsWith<SerializationException>(text) {
                Json.decodeFromString(CurrencyAmountCodeAndDecimalSerializer, text)
            }
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString(CurrencyAmountDecimalSerializer, """{"currency":"USD","amount":"1,234.56"}""")
        }
    }

    test("rejectsAMalformedCombinedString") {
        for (text in listOf("\"USD\"", "\"1234.56\"", "\"\"", "\"USD \"", "\"ZZZ 1.00\"", "\"USD  1.00\"")) {
            assertFailsWith<SerializationException>(text) {
                Json.decodeFromString(CurrencyAmountCodeAndDecimalSerializer, text)
            }
        }
    }

    test("theCombinedStringIsWhatToStringWrites") {
        // So a value copied out of a log reads back in.
        for (amount in listOf(CurrencyAmount(Currency.USD, 1234_56), CurrencyAmount(Currency.JPY, 500))) {
            val encoded = Json.encodeToString(CurrencyAmountCodeAndDecimalSerializer, amount)
            assertEquals("\"$amount\"", encoded)
            assertEquals(amount, Json.decodeFromString(CurrencyAmountCodeAndDecimalSerializer, encoded))
        }
    }

    test("theDecimalFormSurvivesAScaleItsReaderDoesNotShare") {
        // What the decimal form buys over the integer one: the payload says
        // 12.50, so it is 12.50 whatever a reader believes USD's minor units to
        // be. The same amount as minorUnits is 1250 only while both ends agree.
        val text = """{"currency":"USD","amount":"12.50"}"""
        val amount = Json.decodeFromString(CurrencyAmountDecimalSerializer, text)
        assertEquals("12.50", amount.toDecimalString())
        assertEquals(1250L, amount.minorUnits)
    }

    test("worksInsideAGeneratedSerializer") {
        val invoice = Invoice(
            CurrencyAmount(Currency.USD, 1234_56),
            CurrencyAmount(Currency.USD, 99),
            CurrencyAmount(Currency.USD, 1235_55),
        )
        val encoded = Json.encodeToString(invoice)
        assertEquals(
            """{"subtotal":{"currency":"USD","minorUnits":123456},""" +
                """"tax":{"currency":"USD","amount":"0.99"},"total":"USD 1235.55"}""",
            encoded,
        )
        assertEquals(invoice, Json.decodeFromString<Invoice>(encoded))
    }

    test("servesAsAMapKeyInItsCombinedForm") {
        // The point of a single scalar: it fits where an object does not.
        val ledger = mapOf(CurrencyAmount(Currency.USD, 500) to "coffee")
        val encoded = Json.encodeToString(
            MapSerializer(CurrencyAmountCodeAndDecimalSerializer, String.serializer()),
            ledger,
        )
        assertEquals("""{"USD 5.00":"coffee"}""", encoded)
    }

    test("everyActiveCurrencyRoundTripsThroughTheDecimalForms") {
        for (currency in Currency.entries) {
            val amount = CurrencyAmount(currency, 123456)
            val decimals = listOf<KSerializer<CurrencyAmount>>(CurrencyAmountDecimalSerializer, CurrencyAmountCodeAndDecimalSerializer)
            for (serializer in decimals) {
                val encoded = Json.encodeToString(serializer, amount)
                assertEquals(amount, Json.decodeFromString(serializer, encoded), "${currency.code} via $encoded")
            }
        }
    }
}
