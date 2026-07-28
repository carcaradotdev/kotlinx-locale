package dev.carcara.kotlinx.locale.country

import dev.carcara.kotlinx.locale.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-checks the CLDR-generated country names against names extracted from
 * the official ICU repository — a fully independent encoding of the same
 * upstream data, mirroring the datetime module's IcuGoldenTest.
 */
class IcuCountryGoldenTest {

    // Point releases can disagree on non-breaking space variants; normalize.
    private fun String.normalized() = replace('\u00A0', ' ').replace('\u202F', ' ')

    @Test
    fun runtimeNamesMatchIcu() {
        assertTrue(icuCountryGoldenData.size >= 25, "expected the full golden locale set")
        for (golden in icuCountryGoldenData) {
            val locale = Locale.forLanguageTag(golden.tag)
            assertTrue(golden.names.isNotEmpty(), "${golden.tag} has no golden names")
            for ((alpha2, icuName) in golden.names) {
                assertEquals(
                    icuName.normalized(),
                    Country.forAlpha2(alpha2).displayName(locale).normalized(),
                    "${golden.tag} $alpha2",
                )
            }
        }
    }
}
