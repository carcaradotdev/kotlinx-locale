package dev.carcara.kotlinx.locale.currency

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.cldr.displayName
import dev.carcara.kotlinx.locale.currency.cldr.format
import dev.carcara.kotlinx.locale.currency.cldr.symbol
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNotNull
import dev.carcara.kotlinx.locale.test.assertTrue

private val EN = Locale.of("en")
private val HR = Locale.of("hr")

/**
 * A settlement record older than its currency's withdrawal still has to render,
 * and a [CurrencyAmount] needs a [Currency] to render against. So the entry set
 * carries both lists, and these hold the line between them.
 */
val WithdrawnCurrencyTest by matrixSuite {

    test("withdrawnCodesResolveAndFormat") {
        val kuna = assertNotNull(Currency.forCodeOrNull("HRK"), "HRK stopped being tender, it did not stop existing")
        assertTrue(!kuna.isActive, "the kuna was withdrawn when Croatia adopted the euro")
        assertEquals(2, kuna.minorUnitDigits)
        // CLDR carries the names and the symbol for a withdrawn code, so an old
        // record renders in the reader's language rather than as a bare code.
        assertEquals("Croatian Kuna", kuna.displayName(EN))
        assertEquals("kn", kuna.symbol(HR))
        assertEquals("HRK\u00A01,234.56", CurrencyAmount(kuna, 123456).format(EN, style = CurrencySymbolStyle.CODE))
    }

    test("theOtherWithdrawnCodesAreThereToo") {
        for (code in listOf("SLL", "ZWL", "CUC", "DEM", "TRL")) {
            val currency = assertNotNull(Currency.forCodeOrNull(code), "$code is missing")
            assertTrue(!currency.isActive, "$code should not be active")
        }
    }

    test("activeIsTheFilterAPickerWants") {
        val active = Currency.active
        assertEquals(178, active.size, "the active set is ISO list one")
        assertTrue(Currency.entries.size > active.size, "entries carries the withdrawn codes as well")
        assertTrue(active.all(Currency::isActive))
        assertTrue(assertNotNull(Currency.forCodeOrNull("EUR")).isActive)
        assertTrue(!assertNotNull(Currency.forCodeOrNull("HRK")).isActive)
    }

    test("tenderWindowsComeFromCldr") {
        val kuna = assertNotNull(Currency.forCodeOrNull("HRK"))
        // 2023-01-14, the day CLDR records the kuna stopped being tender.
        assertTrue(kuna.lastTenderEpochDay < Int.MAX_VALUE, "a withdrawn code has a closing date")
        assertTrue(kuna.wasTenderOn(kuna.lastTenderEpochDay))
        assertTrue(!kuna.wasTenderOn(kuna.lastTenderEpochDay + 1))
        assertTrue(kuna.isTender, "the kuna was money, it is simply no longer current")

        // A different axis: XXX has never been withdrawn and has never been tender.
        val none = assertNotNull(Currency.forCodeOrNull("XXX"))
        assertTrue(!none.isTender, "XXX is CLDR's no-currency code")
    }

    test("numericLookupPrefersTheActiveEntry") {
        // ISO reuses 191 for the 1991 Croatian dinar and the kuna that replaced
        // it, and 8 for both Albanian leks. The lookup has to pick one.
        val byCode = assertNotNull(Currency.forCodeOrNull("HRK"))
        assertEquals(191, byCode.numericCode)
        // Neither is active today, so the answer is stable rather than correct
        // in some deeper sense; what matters is that it is not ambiguous.
        assertNotNull(Currency.forNumericCodeOrNull(191))
        assertEquals("ALL", assertNotNull(Currency.forNumericCodeOrNull(8)).code, "the active lek owns its number")
    }
}
