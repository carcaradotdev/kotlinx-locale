package dev.carcara.kotlinx.locale.currency.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.normalizedSpaces
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyNameSource
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.currency.displayName
import dev.carcara.kotlinx.locale.currency.forCodeOrNull
import dev.carcara.kotlinx.locale.currency.symbol
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNotNull
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * The currency comparisons that need a golden, next to the golden they read.
 *
 * See `CountryIcuConformance` for why these are no longer in the shared module.
 */

/** Holds this source's symbols and names to ICU's for the golden locales. */
public fun CurrencyNameSource.assertMatchesIcuCurrencyNames() {
    assertTrue(icuCurrencyGoldenData.size >= 25, "expected the full golden locale set")
    for (golden in icuCurrencyGoldenData) {
        val locale = Locale.forLanguageTag(golden.tag)
        for ((code, icuSymbol) in golden.symbols) {
            val currency = assertNotNull(Currency.forCodeOrNull(code), "$code is not in this build's entry set")
            assertEquals(
                icuSymbol.normalizedSpaces(),
                symbol(currency, locale).normalizedSpaces(),
                "${golden.tag} $code symbol",
            )
        }
        for ((code, icuName) in golden.names) {
            val currency = assertNotNull(Currency.forCodeOrNull(code), "$code is not in this build's entry set")
            assertEquals(
                icuName.normalizedSpaces(),
                displayName(currency, locale).normalizedSpaces(),
                "${golden.tag} $code name",
            )
        }
    }
}

/**
 * Cross-checks this build's ISO 4217 numeric codes against ICU's independently
 * maintained table. A property of the entry set rather than of any source, so
 * it takes no receiver.
 */
public fun assertCurrencyNumericCodesMatchIcu() {
    assertTrue(icuCurrencyNumericCodes.size > 250, "expected ICU's full numeric code table")
    var checked = 0
    for (currency in Currency.entries) {
        val icuNumeric = icuCurrencyNumericCodes[currency.code] ?: continue
        assertEquals(icuNumeric, currency.numericCode, "${currency.code} numeric code")
        checked++
    }
    // ICU carries the withdrawn codes too, so this checks well past the active
    // set. The threshold moves with the entry set rather than tracking it
    // exactly, since ICU and ISO do not carry identical historical tables.
    assertTrue(checked > 250, "expected to check both ISO lists against ICU, checked $checked")
}
