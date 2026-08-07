package dev.carcara.kotlinx.locale.currency

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToCurrencyFormats
import dev.carcara.kotlinx.locale.conformance.assertConformsToCurrencyNames
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.conformance.assertCurrencyNumericCodesMatchIcu
import dev.carcara.kotlinx.locale.currency.conformance.assertMatchesIcuCurrencyNames

/**
 * The bundled source is a second encoding of the data ICU encodes, so it is
 * held to the exact tier for names and symbols. Formatted output has no ICU
 * fixture to compare against and is checked by round-tripping instead.
 */
val CldrCurrencyConformanceTest by matrixSuite {

    test("names conform to the source contract at the exact tier") {
        CldrCurrency.assertConformsToCurrencyNames(ConformanceTier.EXACT)
    }

    test("names and symbols match ICU") {
        CldrCurrency.assertMatchesIcuCurrencyNames()
    }

    test("formatting conforms") {
        CldrCurrency.assertConformsToCurrencyFormats(ConformanceTier.EXACT)
    }

    test("numeric codes match ICU") {
        assertCurrencyNumericCodesMatchIcu()
    }
}
