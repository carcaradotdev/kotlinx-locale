package dev.carcara.kotlinx.locale.country

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToCountryNames
import dev.carcara.kotlinx.locale.country.cldr.CldrCountry
import dev.carcara.kotlinx.locale.country.platform.PlatformCountry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the platform source is expected to do, and what it is not.
 *
 * The composition is the thing an application ships, so that is what the
 * conformance suite runs against. `PlatformCountry` on its own is allowed gaps:
 * the host decides what it knows, and on four of the targets it knows nothing.
 */
class PlatformCountryTest {

    private val composed = FallbackCountryNames(primary = PlatformCountry, fallback = CldrCountry)

    @Test
    fun theCompositionConformsBehaviourally() {
        composed.assertConformsToCountryNames(ConformanceTier.BEHAVIOURAL)
    }

    @Test
    fun theCompositionAnswersEverywhereEvenWhereThePlatformDoesNot() {
        // This is the property that makes the platform layer usable: whatever the
        // host is missing, the bundled source covers, and the caller sees one
        // source that always answers.
        for (tag in listOf("en", "pt-BR", "de", "ja", "ar-EG")) {
            val locale = Locale.forLanguageTag(tag)
            for (country in Country.entries) {
                val name = composed.displayName(country, locale)
                assertTrue(name.isNotBlank(), "$tag ${country.alpha2} was blank")
            }
        }
    }

    @Test
    fun theUnavailableTargetsSaySoRatherThanAnsweringBadly() {
        if (PlatformCountry.isAvailable) return
        // Linux, Windows, Android Native and WASI. A source that returned the ISO
        // code here would look like an answer and stop any fallback from firing.
        assertEquals(null, PlatformCountry.countryNameOrNull("BR", Locale.of("en")))
        assertTrue(PlatformCountry.supportedLocales.isEmpty())
    }

    @Test
    fun theAvailableTargetsNameTheMajorCountries() {
        if (!PlatformCountry.isAvailable) return
        val en = Locale.of("en")
        for (alpha2 in listOf("BR", "DE", "JP", "US", "FR")) {
            val name = assertNotNull(
                PlatformCountry.countryNameOrNull(alpha2, en),
                "the platform has no English name for $alpha2",
            )
            // Never the code echoed back: that is what countryNameOrNull filters,
            // and it is what keeps a composing source honest.
            assertTrue(!name.equals(alpha2, ignoreCase = true), "$alpha2 came back as its own code")
            assertTrue(name.isNotBlank())
        }
        assertEquals("Brazil", PlatformCountry.countryNameOrNull("BR", en))
    }

    @Test
    fun theAvailableTargetsLocalizeAndNotJustTranslateToEnglish() {
        if (!PlatformCountry.isAvailable) return
        val english = PlatformCountry.countryNameOrNull("DE", Locale.of("en"))
        val german = PlatformCountry.countryNameOrNull("DE", Locale.of("de"))
        assertNotNull(english)
        assertNotNull(german)
        assertTrue(english != german, "the platform returned '$english' for both en and de")
    }

    @Test
    fun anUnassignedCodeIsAnswearedByTheHostRatherThanRefused() {
        // ZZ looks like a region code and nobody assigns it. CldrCountry returns
        // null. java.util.Locale returns a localized "Unknown Region", which the
        // echo filter cannot catch because it is a name, not the code.
        //
        // Pinning this rather than smoothing it over: the two sources genuinely
        // disagree here, and the disagreement is unreachable through the public
        // API, because the only codes that reach a source come from the Country
        // enum and every one of those is assigned. Adding per-platform validation
        // to hide it would be complexity for a case no caller can produce.
        val name = PlatformCountry.countryNameOrNull("ZZ", Locale.of("en"))
        if (PlatformCountry.isAvailable) {
            assertTrue(
                name == null || name != "ZZ",
                "an unassigned code should miss or be named, never echoed: got '$name'",
            )
        } else {
            assertEquals(null, name)
        }
    }
}
