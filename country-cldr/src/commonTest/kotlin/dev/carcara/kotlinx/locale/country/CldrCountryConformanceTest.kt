package dev.carcara.kotlinx.locale.country

import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToCountryNames
import dev.carcara.kotlinx.locale.country.cldr.CldrCountry
import kotlin.test.Test

/**
 * The bundled source is a second encoding of the data ICU encodes, so it is
 * held to the exact tier: every name matches ICU byte for byte.
 */
class CldrCountryConformanceTest {

    @Test
    fun conformsExactly() = CldrCountry.assertConformsToCountryNames(ConformanceTier.EXACT)
}
