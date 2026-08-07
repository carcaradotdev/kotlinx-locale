package dev.carcara.kotlinx.locale.currency

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertFailsWith
import dev.carcara.kotlinx.locale.test.assertNull
import dev.carcara.kotlinx.locale.test.assertTrue

val CurrencyAmountTest by matrixSuite {

    test("decomposesIntoMajorAndMinorParts") {
        val amount = CurrencyAmount(Currency.USD, 1250)
        assertEquals(12, amount.majorUnits)
        assertEquals(50, amount.minorPart)

        val negative = CurrencyAmount(Currency.USD, -1250)
        assertEquals(-12, negative.majorUnits)
        assertEquals(-50, negative.minorPart)

        val yen = CurrencyAmount(Currency.JPY, 1234)
        assertEquals(1234, yen.majorUnits)
        assertEquals(0, yen.minorPart)

        val dinar = CurrencyAmount(Currency.BHD, 1500)
        assertEquals(1, dinar.majorUnits)
        assertEquals(500, dinar.minorPart)
    }

    test("buildsFromMajorAndMinorParts") {
        assertEquals(1250, CurrencyAmount.of(Currency.USD, 12, 50).minorUnits)
        assertEquals(-1250, CurrencyAmount.of(Currency.USD, -12, -50).minorUnits)
        assertEquals(-5, CurrencyAmount.of(Currency.USD, 0, -5).minorUnits)
        assertEquals(1234, CurrencyAmount.of(Currency.JPY, 1234).minorUnits)
        assertEquals(1500, CurrencyAmount.of(Currency.BHD, 1, 500).minorUnits)

        assertFailsWith<IllegalArgumentException> { CurrencyAmount.of(Currency.USD, 12, 100) }
        assertFailsWith<IllegalArgumentException> { CurrencyAmount.of(Currency.USD, 12, -5) }
        assertFailsWith<IllegalArgumentException> { CurrencyAmount.of(Currency.JPY, 12, 1) }
    }

    test("supportsArithmeticWithinOneCurrency") {
        val a = CurrencyAmount(Currency.USD, 1000)
        val b = CurrencyAmount(Currency.USD, 250)
        assertEquals(1250, (a + b).minorUnits)
        assertEquals(750, (a - b).minorUnits)
        assertEquals(-1000, (-a).minorUnits)
        assertTrue(a > b)
        assertTrue(b < a)

        val yen = CurrencyAmount(Currency.JPY, 100)
        assertFailsWith<IllegalArgumentException> { a + yen }
        assertFailsWith<IllegalArgumentException> { a - yen }
        assertFailsWith<IllegalArgumentException> { a.compareTo(yen) }
    }

    test("amountsAreValueObjects") {
        assertEquals(CurrencyAmount(Currency.USD, 1250), CurrencyAmount(Currency.USD, 1250))
        assertTrue(CurrencyAmount(Currency.USD, 1250) != CurrencyAmount(Currency.EUR, 1250))
        assertTrue(CurrencyAmount(Currency.USD, 1250) != CurrencyAmount(Currency.USD, 1251))
        assertEquals(
            CurrencyAmount(Currency.USD, 1250).hashCode(),
            CurrencyAmount(Currency.USD, 1250).hashCode(),
        )
        assertEquals("USD 12.50", CurrencyAmount(Currency.USD, 1250).toString())
    }

    test("rendersIsoDecimalStrings") {
        assertEquals("12.50", CurrencyAmount(Currency.USD, 1250).toDecimalString())
        assertEquals("-12.50", CurrencyAmount(Currency.USD, -1250).toDecimalString())
        assertEquals("-0.05", CurrencyAmount(Currency.USD, -5).toDecimalString())
        assertEquals("0.00", CurrencyAmount(Currency.USD, 0).toDecimalString())
        assertEquals("5", CurrencyAmount(Currency.JPY, 5).toDecimalString())
        assertEquals("1.500", CurrencyAmount(Currency.BHD, 1500).toDecimalString())
    }

    test("parsesIsoDecimalStrings") {
        assertEquals(1250, CurrencyAmount.parse(Currency.USD, "12.50").minorUnits)
        assertEquals(1250, CurrencyAmount.parse(Currency.USD, "12.5").minorUnits)
        assertEquals(-700, CurrencyAmount.parse(Currency.USD, "-7").minorUnits)
        assertEquals(50, CurrencyAmount.parse(Currency.USD, ".5").minorUnits)
        assertEquals(5, CurrencyAmount.parse(Currency.JPY, "5").minorUnits)
        assertEquals(1500, CurrencyAmount.parse(Currency.BHD, "1.5").minorUnits)

        assertNull(CurrencyAmount.parseOrNull(Currency.USD, ""))
        assertNull(CurrencyAmount.parseOrNull(Currency.USD, "-"))
        assertNull(CurrencyAmount.parseOrNull(Currency.USD, "12."))
        assertNull(CurrencyAmount.parseOrNull(Currency.USD, "12.345"))
        assertNull(CurrencyAmount.parseOrNull(Currency.JPY, "5.0"))
        assertNull(CurrencyAmount.parseOrNull(Currency.USD, "1,234.00"))
        assertNull(CurrencyAmount.parseOrNull(Currency.USD, "abc"))
        assertNull(CurrencyAmount.parseOrNull(Currency.USD, "99999999999999999999"))
        assertFailsWith<IllegalArgumentException> { CurrencyAmount.parse(Currency.USD, "abc") }
    }

    test("decimalStringsRoundTripForEveryCurrency") {
        val samples = listOf(0L, 1, -1, 99, -100, 123456, -9999999, Long.MAX_VALUE, Long.MIN_VALUE)
        for (currency in Currency.entries) {
            for (minorUnits in samples) {
                val amount = CurrencyAmount(currency, minorUnits)
                assertEquals(
                    amount,
                    CurrencyAmount.parseOrNull(currency, amount.toDecimalString()),
                    "${currency.code} $minorUnits",
                )
            }
        }
    }

    test("handlesLongExtremes") {
        val max = CurrencyAmount(Currency.USD, Long.MAX_VALUE)
        assertEquals("92233720368547758.07", max.toDecimalString())
        assertEquals(92233720368547758, max.majorUnits)
        assertEquals(7, max.minorPart)

        val min = CurrencyAmount(Currency.USD, Long.MIN_VALUE)
        assertEquals("-92233720368547758.08", min.toDecimalString())
        assertEquals(-92233720368547758, min.majorUnits)
        assertEquals(-8, min.minorPart)

        assertEquals(Long.MIN_VALUE, CurrencyAmount.parse(Currency.JPY, "-9223372036854775808").minorUnits)
        assertNull(CurrencyAmount.parseOrNull(Currency.JPY, "-9223372036854775809"))
        assertNull(CurrencyAmount.parseOrNull(Currency.JPY, "9223372036854775808"))
    }
}
