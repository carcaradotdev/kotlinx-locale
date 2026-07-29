package dev.carcara.kotlinx.locale.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.CountryNameSource
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The suite is only worth having if it fails on a source that is wrong, so
 * that is what these check. They also pin down what the behavioural tier costs
 * a source that carries no CLDR data at all, which is the tier the platform
 * layer will be held to.
 */
class ConformanceSuiteTest {

    private fun source(name: (String) -> String?) = object : CountryNameSource {
        override val supportedLocales: Set<Locale> = setOf(Locale.of("en"))
        override fun countryNameOrNull(alpha2: String, locale: Locale): String? = name(alpha2)
    }

    @Test
    fun acceptsANonCldrSourceThatIsWellShaped() {
        source { "Country $it" }.assertConformsToCountryNames(ConformanceTier.BEHAVIOURAL)
    }

    @Test
    fun rejectsASourceThatNamesNothing() {
        assertFailsWith<AssertionError> {
            source { null }.assertConformsToCountryNames(ConformanceTier.BEHAVIOURAL)
        }
    }

    @Test
    fun rejectsASourceThatGivesEveryCountryTheSameName() {
        // Reverse lookup would answer, but with a country that is not the one
        // asked about, which is exactly the bug the round trip exists to catch.
        assertFailsWith<AssertionError> {
            source { "Somewhere" }.assertConformsToCountryNames(ConformanceTier.BEHAVIOURAL)
        }
    }

    @Test
    fun rejectsASourceThatSupportsNoLocale() {
        val nothing = object : CountryNameSource {
            override val supportedLocales: Set<Locale> = emptySet()
            override fun countryNameOrNull(alpha2: String, locale: Locale): String = "Country $alpha2"
        }
        assertFailsWith<AssertionError> { nothing.assertConformsToCountryNames(ConformanceTier.BEHAVIOURAL) }
    }

    @Test
    fun rejectsANonCldrSourceAtTheExactTier() {
        assertFailsWith<AssertionError> {
            source { "Country $it" }.assertConformsToCountryNames(ConformanceTier.EXACT)
        }
    }
}
