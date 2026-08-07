package dev.carcara.kotlinx.locale.country.conformance

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.CountryNameSource
import dev.carcara.kotlinx.locale.test.assertFailsWith

/**
 * The negative case for the ICU comparison, which moved here with the golden.
 *
 * `conformance-test-suite` used to own this: its exact tier called the ICU
 * comparison, so a well-shaped source that was not CLDR failed there. The
 * comparison is now next to the fixture it reads, and so is the proof that it
 * rejects something. Without this, `assertMatchesIcuCountryNames` could be
 * gutted to a no-op and every conformance test in the build would stay green.
 */
val CountryIcuConformanceRejection by matrixSuite {

    test("rejects a well-shaped source that is not CLDR") {
        val plausible = object : CountryNameSource {
            override val supportedLocales: Set<Locale> = setOf(Locale.of("en"))
            override fun countryNameOrNull(alpha2: String, locale: Locale): String = "Country $alpha2"
        }
        assertFailsWith<AssertionError> { plausible.assertMatchesIcuCountryNames() }
    }
}
