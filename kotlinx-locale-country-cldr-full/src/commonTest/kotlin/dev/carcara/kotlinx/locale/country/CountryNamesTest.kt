package dev.carcara.kotlinx.locale.country

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.cldr.CldrCountry
import dev.carcara.kotlinx.locale.country.cldr.displayName
import dev.carcara.kotlinx.locale.country.cldr.forDisplayNameOrNull
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNotEquals
import dev.carcara.kotlinx.locale.test.assertNotNull
import dev.carcara.kotlinx.locale.test.assertNull
import dev.carcara.kotlinx.locale.test.assertTrue

val CountryNamesTest by matrixSuite {

    fun locale(tag: String) = Locale.forLanguageTag(tag)

    test("localizesNames") {
        assertEquals("United States", Country.US.displayName(locale("en")))
        assertEquals("Estados Unidos", Country.US.displayName(locale("pt")))
        assertEquals("Deutschland", Country.DE.displayName(locale("de")))
        assertEquals("Allemagne", Country.DE.displayName(locale("fr")))
        assertEquals("日本", Country.JP.displayName(locale("ja")))
        assertEquals("美国", Country.US.displayName(locale("zh")))
        assertEquals("Côte d’Ivoire", Country.CI.displayName(locale("en")))
    }

    test("inheritsNamesFromTheParentLocale") {
        // de-AT and pt-BR declare no name of their own for these countries.
        assertEquals("Deutschland", Country.DE.displayName(locale("de-AT")))
        assertEquals("Estados Unidos", Country.US.displayName(locale("pt-BR")))
        assertEquals("United Kingdom", Country.GB.displayName(locale("en-AU")))
    }

    test("honorsCldrParentLocaleOverrides") {
        // es-AR inherits from es-419 (a CLDR parentLocales override), which
        // renames CI relative to plain es.
        assertEquals("Côte d’Ivoire", Country.CI.displayName(locale("es")))
        assertEquals("Costa de Marfil", Country.CI.displayName(locale("es-419")))
        assertEquals("Costa de Marfil", Country.CI.displayName(locale("es-AR")))
    }

    test("fallsBackToTheAlpha2Code") {
        // A valid but unknown language reaches CLDR root, which carries no names.
        assertEquals("US", Country.US.displayName(locale("xx")))
        assertEquals("BR", Country.BR.displayName(locale("zz")))
    }

    test("findsCountriesByDisplayName") {
        val en = locale("en")
        assertEquals(Country.US, Country.forDisplayNameOrNull("United States", en))
        assertEquals(Country.US, Country.forDisplayNameOrNull("united states", en))
        assertEquals(Country.US, Country.forDisplayNameOrNull("  United States  ", en))
        assertEquals(Country.US, Country.forDisplayNameOrNull("Estados Unidos", locale("pt")))
        assertEquals(Country.DE, Country.forDisplayNameOrNull("Allemagne", locale("fr")))
        assertNull(Country.forDisplayNameOrNull("Atlantis", en))
        assertNull(Country.forDisplayNameOrNull("", en))
    }

    test("everyCountryHasAnEnglishName") {
        val en = locale("en")
        for (country in Country.entries) {
            val name = country.displayName(en)
            assertTrue(name.isNotBlank(), "${country.alpha2} name was blank")
            assertNotEquals(country.alpha2, name, "${country.alpha2} fell back to its code in en")
        }
    }

    test("englishNamesRoundTripThroughReverseLookup") {
        val en = locale("en")
        for (country in Country.entries) {
            assertEquals(
                country,
                Country.forDisplayNameOrNull(country.displayName(en), en),
                "${country.alpha2} did not round trip",
            )
        }
    }

    test("majorLocalesNameEveryCountry") {
        for (tag in listOf("en", "pt", "es", "fr", "de", "ja", "ru", "zh")) {
            val locale = locale(tag)
            for (country in Country.entries) {
                val name = country.displayName(locale)
                assertTrue(name.isNotBlank(), "$tag ${country.alpha2} was blank")
                assertNotEquals(country.alpha2, name, "$tag ${country.alpha2} fell back to its code")
            }
        }
    }

    test("reverseLookupIsSoundInMajorLocales") {
        // Some locales give two countries the same name, so reverse lookup must
        // return a country carrying exactly the requested name, though not
        // necessarily the one that produced it.
        for (tag in listOf("pt", "ja", "ru")) {
            val locale = locale(tag)
            for (country in Country.entries) {
                val name = country.displayName(locale)
                val found = Country.forDisplayNameOrNull(name, locale)
                assertNotNull(found, "$tag '$name' found nothing")
                assertEquals(name, found.displayName(locale), "$tag '$name' mismapped")
            }
        }
    }

    test("reportsTheLocalesItCarriesDataFor") {
        val tags = CldrCountry.supportedLocales.map(Locale::toLanguageTag)
        assertTrue(tags.size > 700, "expected hundreds of locales, got ${tags.size}")
        assertTrue("en" in tags)
        assertTrue("en-GB" in tags)
        assertTrue("pt-BR" in tags)
        assertTrue("zh-Hant" in tags)
        assertTrue("root" !in tags)
    }

    test("everyLocaleResolvesANameForEveryCountry") {
        for (locale in CldrCountry.supportedLocales) {
            for (country in listOf(Country.US, Country.BR, Country.JP, Country.DE, Country.EG, Country.IN)) {
                assertTrue(
                    country.displayName(locale).isNotBlank(),
                    "$locale ${country.alpha2} was blank",
                )
            }
        }
    }
}
