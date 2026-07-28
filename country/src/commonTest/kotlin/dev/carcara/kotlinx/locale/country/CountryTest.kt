package dev.carcara.kotlinx.locale.country

import dev.carcara.kotlinx.locale.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountryTest {

    @Test
    fun exposesIsoCodes() {
        assertEquals("US", Country.US.alpha2)
        assertEquals("USA", Country.US.alpha3)
        assertEquals(840, Country.US.numericCode)

        assertEquals("BRA", Country.BR.alpha3)
        assertEquals(76, Country.BR.numericCode)

        assertEquals("DEU", Country.DE.alpha3)
        assertEquals(276, Country.DE.numericCode)

        assertEquals("AND", Country.AD.alpha3)
        assertEquals(20, Country.AD.numericCode)
    }

    @Test
    fun coversTheIsoCountrySet() {
        assertTrue(
            Country.entries.size in 240..260,
            "expected the ISO 3166-1 set, got ${Country.entries.size}",
        )
        for (country in Country.entries) {
            assertEquals(2, country.alpha2.length, "${country.alpha2} alpha2")
            assertEquals(3, country.alpha3.length, "${country.alpha2} alpha3")
            assertTrue(country.numericCode in 1..999, "${country.alpha2} numeric")
        }
        assertEquals(
            Country.entries.size,
            Country.entries.map(Country::alpha3).toSet().size,
            "alpha3 codes must be unique",
        )
        assertEquals(
            Country.entries.size,
            Country.entries.map(Country::numericCode).toSet().size,
            "numeric codes must be unique",
        )
    }

    @Test
    fun excludesNonIsoCldrRegions() {
        // Macroregions, exceptionally reserved codes and user-assigned codes
        // are CLDR regions but not ISO 3166-1 countries.
        for (code in listOf("EU", "EZ", "UN", "AC", "IC", "TA", "XK", "ZZ", "QO")) {
            assertNull(Country.forAlpha2OrNull(code), code)
        }
    }

    @Test
    fun mapsBetweenAllRepresentations() {
        for (country in Country.entries) {
            assertEquals(country, Country.forAlpha2(country.alpha2))
            assertEquals(country, Country.forAlpha3(country.alpha3))
            assertEquals(country, Country.forNumericCode(country.numericCode))
        }
    }

    @Test
    fun parsesCodesCaseInsensitively() {
        assertEquals(Country.US, Country.forAlpha2OrNull("us"))
        assertEquals(Country.US, Country.forAlpha3OrNull("usa"))
        assertEquals(Country.BR, Country.forAlpha2OrNull("bR"))
    }

    @Test
    fun rejectsUnknownCodes() {
        assertNull(Country.forAlpha2OrNull("XX"))
        assertNull(Country.forAlpha3OrNull("ZZZ"))
        assertNull(Country.forNumericCodeOrNull(0))
        assertFailsWith<IllegalArgumentException> { Country.forAlpha2("XX") }
        assertFailsWith<IllegalArgumentException> { Country.forAlpha3("ZZZ") }
        assertFailsWith<IllegalArgumentException> { Country.forNumericCode(0) }
    }

    @Test
    fun resolvesTheLocaleRegion() {
        assertEquals(Country.BR, Country.forLocaleOrNull(Locale.forLanguageTag("pt-BR")))
        assertEquals(Country.US, Country.forLocaleOrNull(Locale.forLanguageTag("en-US")))
        assertNull(Country.forLocaleOrNull(Locale.forLanguageTag("en")))
        assertNull(Country.forLocaleOrNull(Locale.forLanguageTag("es-419")))
    }
}
