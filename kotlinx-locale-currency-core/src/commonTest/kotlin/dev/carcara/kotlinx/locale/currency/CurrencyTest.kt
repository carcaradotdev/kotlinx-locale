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

package dev.carcara.kotlinx.locale.currency

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertFailsWith
import dev.carcara.kotlinx.locale.test.assertNull
import dev.carcara.kotlinx.locale.test.assertTrue

val CurrencyTest by matrixSuite {

    test("exposesIsoData") {
        assertEquals("USD", Currency.USD.code)
        assertEquals(840, Currency.USD.numericCode)
        assertEquals(2, Currency.USD.defaultFractionDigits)

        assertEquals(978, Currency.EUR.numericCode)
        assertEquals(392, Currency.JPY.numericCode)
        assertEquals(0, Currency.JPY.defaultFractionDigits)
        assertEquals(48, Currency.BHD.numericCode)
        assertEquals(3, Currency.BHD.defaultFractionDigits)
        assertEquals(999, Currency.XXX.numericCode)
    }

    test("exposesIsoVersusCldrDecimalCases") {
        // CLDR intentionally formats some currencies with fewer digits than ISO defines.
        assertEquals(2, Currency.ALL.defaultFractionDigits)
        assertEquals(0, Currency.ALL.cldrFractionDigits)

        assertEquals(2, Currency.USD.defaultFractionDigits)
        assertEquals(2, Currency.USD.cldrFractionDigits)

        // Cash-specific cases.
        assertEquals(5, Currency.CHF.cldrCashRoundingIncrement)
        assertEquals(50, Currency.DKK.cldrCashRoundingIncrement)
        assertEquals(0, Currency.AMD.cldrCashFractionDigits)
        assertEquals(2, Currency.AMD.cldrFractionDigits)

        // Metals and special codes have no ISO minor units.
        assertEquals(-1, Currency.XAU.defaultFractionDigits)
        assertEquals(0, Currency.XAU.minorUnitDigits)
        assertEquals(-1, Currency.XXX.defaultFractionDigits)
    }

    test("coversBothIsoLists") {
        assertTrue(
            Currency.active.size > 150,
            "expected the active ISO 4217 set, got ${Currency.active.size}",
        )
        assertTrue(
            Currency.entries.size > Currency.active.size,
            "entries carries the withdrawn codes as well, so an old record can render",
        )
        for (currency in Currency.entries) {
            assertEquals(3, currency.code.length, "${currency.code} code")
            assertTrue(currency.cldrFractionDigits >= 0, "${currency.code} cldr digits")
        }
        // ISO reuses a numeric code across generations of the same currency, so
        // uniqueness only holds within the active set. The lookup by number
        // resolves the rest to the active entry.
        val numerics = Currency.active.filter { it.numericCode >= 0 }.map(Currency::numericCode)
        assertEquals(numerics.size, numerics.toSet().size, "active numeric codes must be unique")
    }

    test("mapsBetweenRepresentations") {
        for (currency in Currency.entries) {
            assertEquals(currency, Currency.forCode(currency.code))
        }
        // Only the active entry owns its number: 8 is both Albanian leks and 191
        // is both Croatian currencies, so a withdrawn code does not round trip
        // through its number.
        for (currency in Currency.active) {
            if (currency.numericCode >= 0) {
                assertEquals(currency, Currency.forNumericCode(currency.numericCode))
            }
        }
        assertEquals(Currency.USD, Currency.forCodeOrNull("usd"))
        assertNull(Currency.forCodeOrNull("ZZZ"))
        assertNull(Currency.forNumericCodeOrNull(0))
        assertFailsWith<IllegalArgumentException> { Currency.forCode("ZZZ") }
        assertFailsWith<IllegalArgumentException> { Currency.forNumericCode(0) }
    }

    test("convertsBetweenIsoAndCldrScales") {
        // ALL: ISO 2 decimals, CLDR 0 -> divide by 100, half-even.
        assertEquals(123, Currency.ALL.isoToCldrUnits(12345))
        assertEquals(124, Currency.ALL.isoToCldrUnits(12350)) // tie, 123 is odd -> away
        assertEquals(122, Currency.ALL.isoToCldrUnits(12250)) // tie, 122 is even -> stay
        assertEquals(-124, Currency.ALL.isoToCldrUnits(-12350))
        assertEquals(12300, Currency.ALL.cldrToIsoUnits(123))

        // Same scale on both sides is the identity.
        assertEquals(1234, Currency.JPY.isoToCldrUnits(1234))
        assertEquals(1234, Currency.USD.isoToCldrUnits(1234))
        assertEquals(1234567, Currency.BHD.isoToCldrUnits(1234567))

        // XAU: no ISO minor units, CLDR formats with 2 digits.
        assertEquals(500, Currency.XAU.isoToCldrUnits(5))
        assertEquals(5, Currency.XAU.cldrToIsoUnits(500))
    }

    test("mapsCountriesToCurrencies") {
        assertEquals(Currency.USD, Country.US.currency)
        assertEquals(Currency.BRL, Country.BR.currency)
        assertEquals(Currency.JPY, Country.JP.currency)
        assertEquals(Currency.EUR, Country.DE.currency)
        assertEquals(Currency.CHF, Country.CH.currency)

        // Antarctica has no universal currency.
        assertNull(Country.AQ.currency)
        assertTrue(Country.AQ.currencies.isEmpty())

        // Panama uses both its own balboa and the US dollar.
        assertTrue(Currency.PAB in Country.PA.currencies)
        assertTrue(Currency.USD in Country.PA.currencies)

        assertEquals(Currency.EUR, Currency.forCountryOrNull(Country.DE))
        assertEquals(Currency.BRL, Currency.forLocaleOrNull(Locale.forLanguageTag("pt-BR")))
        assertNull(Currency.forLocaleOrNull(Locale.forLanguageTag("pt")))
    }

    test("enumWideDataInvariantsHold") {
        for (currency in Currency.entries) {
            assertTrue(currency.defaultFractionDigits in -1..4, "${currency.code} iso digits")
            assertTrue(currency.cldrFractionDigits in 0..4, "${currency.code} cldr digits")
            assertTrue(currency.cldrCashFractionDigits in 0..4, "${currency.code} cash digits")
            assertTrue(currency.cldrRoundingIncrement >= 0, "${currency.code} rounding")
            assertTrue(currency.cldrCashRoundingIncrement >= 0, "${currency.code} cash rounding")
            assertTrue(currency.minorUnitDigits >= 0, "${currency.code} minor unit digits")
        }
    }

    test("scaleConversionsRoundTripWheneverCldrKeepsAllDigits") {
        val samples = listOf(0L, 1, -1, 12345, -99999, 10_000_000_000)
        for (currency in Currency.entries) {
            if (currency.cldrFractionDigits < currency.minorUnitDigits) continue
            for (value in samples) {
                assertEquals(
                    value,
                    currency.cldrToIsoUnits(currency.isoToCldrUnits(value)),
                    "${currency.code} $value",
                )
            }
        }
    }

    test("everyCountryWithACurrencyResolvesActiveCodes") {
        for (country in Country.entries) {
            for (currency in country.currencies) {
                assertTrue(currency.code.length == 3, "${country.alpha2} -> ${currency.code}")
            }
        }
        val covered = Country.entries.count { it.currency != null }
        assertTrue(covered > 240, "expected nearly all countries to map to a currency, got $covered")
    }
}
