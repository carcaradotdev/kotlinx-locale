package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToCurrencyFormats
import dev.carcara.kotlinx.locale.conformance.assertConformsToCurrencyNames
import dev.carcara.kotlinx.locale.conformance.assertCurrencyNumericCodesMatchIcu
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import kotlin.test.Test

/**
 * The bundled source is a second encoding of the data ICU encodes, so it is
 * held to the exact tier for names and symbols. Formatted output has no ICU
 * fixture to compare against and is checked by round-tripping instead.
 */
class CldrCurrencyConformanceTest {

    @Test
    fun namesConformExactly() = CldrCurrency.assertConformsToCurrencyNames(ConformanceTier.EXACT)

    @Test
    fun formattingConforms() = CldrCurrency.assertConformsToCurrencyFormats(ConformanceTier.EXACT)

    @Test
    fun numericCodesMatchIcu() = assertCurrencyNumericCodesMatchIcu()
}
