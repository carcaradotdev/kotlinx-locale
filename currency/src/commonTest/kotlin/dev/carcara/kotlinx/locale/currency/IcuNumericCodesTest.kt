package dev.carcara.kotlinx.locale.currency

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies every enum entry's ISO 4217 numeric code against ICU's
 * independently maintained table, on every platform. The vendored ISO list
 * and ICU are separate encodings of the standard, so agreement here checks
 * both the list itself and the generator's parse of it.
 */
class IcuNumericCodesTest {

    @Test
    fun everyNumericCodeMatchesIcu() {
        var checked = 0
        for (currency in Currency.entries) {
            val icuNumeric = icuCurrencyNumericCodes[currency.code] ?: continue
            assertEquals(icuNumeric, currency.numericCode, currency.code)
            checked++
        }
        assertTrue(checked > 170, "only $checked currencies were cross-checked against ICU")
    }
}
