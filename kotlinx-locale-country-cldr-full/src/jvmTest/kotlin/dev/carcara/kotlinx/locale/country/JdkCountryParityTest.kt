package dev.carcara.kotlinx.locale.country

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.test.assertEquals

/**
 * Cross-checks the generated ISO 3166-1 data against the JDK's own tables —
 * a third independent source next to CLDR and ICU, available on JVM only.
 */
val JdkCountryParityTest by matrixSuite {

    test("alpha2SetMatchesTheJdk") {
        val jdk = java.util.Locale.getISOCountries().toSortedSet()
        val ours = Country.entries.map(Country::alpha2).toSortedSet()
        assertEquals(jdk, ours)
    }

    test("alpha3CodesMatchTheJdk") {
        for (country in Country.entries) {
            assertEquals(
                java.util.Locale.of("", country.alpha2).isO3Country,
                country.alpha3,
                country.alpha2,
            )
        }
    }
}
