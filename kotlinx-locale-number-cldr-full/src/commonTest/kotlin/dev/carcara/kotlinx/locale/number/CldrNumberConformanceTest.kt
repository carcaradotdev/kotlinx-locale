package dev.carcara.kotlinx.locale.number

import dev.carcara.kotlinx.locale.conformance.assertConformsToCldrPluralSamples
import dev.carcara.kotlinx.locale.conformance.assertConformsToIcuNumbers
import dev.carcara.kotlinx.locale.conformance.assertNumbersAreWellShaped
import dev.carcara.kotlinx.locale.number.cldr.CldrNumber
import dev.carcara.kotlinx.locale.number.cldr.CldrNumberPlurals
import kotlin.test.Test

/**
 * The bundled source is a second encoding of the data CLDR ships, so it is held
 * to CLDR's own samples rather than only to the shape of its answers.
 */
class CldrNumberConformanceTest {

    @Test
    fun pluralRulesAgreeWithCldrsOwnSamples() = CldrNumberPlurals.assertConformsToCldrPluralSamples()

    @Test
    fun numbersAreWellShaped() = CldrNumber.assertNumbersAreWellShaped()

    @Test
    fun formattingAgreesWithIcu() = CldrNumber.assertConformsToIcuNumbers()
}
