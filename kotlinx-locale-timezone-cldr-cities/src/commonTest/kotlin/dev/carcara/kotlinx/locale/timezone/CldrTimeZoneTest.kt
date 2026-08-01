package dev.carcara.kotlinx.locale.timezone

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.timezone.cldr.CldrTimeZone
import dev.carcara.kotlinx.locale.timezone.cldr.cities.exemplarCity
import dev.carcara.kotlinx.locale.timezone.cldr.cities.localizedName
import dev.carcara.kotlinx.locale.timezone.cldr.displayName
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val EN = Locale.of("en")
private val CS = Locale.of("cs")
private val PT = Locale.of("pt")

/**
 * A zone this platform can build, or `null`.
 *
 * kotlinx-datetime reads whichever copy of the IANA time zone database the
 * target has, and Kotlin/JS under Node has no full one, so `TimeZone.of` throws
 * there for identifiers every other target accepts. Naming a zone does not
 * depend on that: this library works from the identifier and the tables, and
 * never constructs a zone itself. So the tests that need a constructed zone skip
 * where the platform cannot make one, and the ones that do not keep running
 * everywhere.
 */
private fun zoneOrNull(id: String): TimeZone? = try {
    TimeZone.of(id)
} catch (e: IllegalTimeZoneException) {
    null
}

class CldrTimeZoneTest {

    @Test
    fun theOffsetFormatIsLocaleDataRatherThanAFixedString() {
        // The word, the digits and the zero form all vary, which is what a
        // hand-built "UTC±HH:MM" gets wrong in every locale but one.
        assertEquals("GMT-08:00", UtcOffset(hours = -8).displayName(EN))
        assertEquals("GMT+05:30", UtcOffset(hours = 5, minutes = 30).displayName(EN))
        assertEquals("GMT", UtcOffset(hours = 0).displayName(EN))
        assertEquals("GMT-8", UtcOffset(hours = -8).displayName(EN, short = true))
        assertTrue(UtcOffset(hours = -8).displayName(CS).isNotBlank())
    }

    @Test
    fun namesAZoneThroughItsMetazone() {
        val losAngeles = zoneOrNull("America/Los_Angeles") ?: return
        assertEquals("Pacific Time", losAngeles.displayName(TimeZoneNameStyle.GENERIC_LONG, locale = EN))
        assertEquals("Pacific Standard Time", losAngeles.displayName(TimeZoneNameStyle.STANDARD_LONG, locale = EN))
        assertEquals("Pacific Daylight Time", losAngeles.displayName(TimeZoneNameStyle.DAYLIGHT_LONG, locale = EN))
        assertEquals("PST", losAngeles.displayName(TimeZoneNameStyle.STANDARD_SHORT, locale = EN))
    }

    @Test
    fun aZoneWithoutDaylightTimeStillHasBothForms() {
        // Japan does not shift, and CLDR still declares a generic name for it.
        // Where a locale declares none, UTS #35 says the generic form falls back
        // to the standard one, which is what the source does.
        val tokyo = zoneOrNull("Asia/Tokyo") ?: return
        assertEquals("Japan Time", tokyo.displayName(TimeZoneNameStyle.GENERIC_LONG, locale = EN))
        assertEquals("Japan Standard Time", tokyo.displayName(TimeZoneNameStyle.STANDARD_LONG, locale = EN))
    }

    @Test
    fun theLocationFormatNamesTheRegionWhereItHasOneZone() {
        // Japan has one zone, so the location format names the country rather
        // than the city. Without country names it falls back to the code, which
        // is the degradation the spec prescribes.
        val tokyo = zoneOrNull("Asia/Tokyo") ?: return
        val losAngeles = zoneOrNull("America/Los_Angeles") ?: return
        assertTrue(tokyo.localizedName(TimeZoneNameStyle.LOCATION, locale = EN).isNotBlank())
        assertEquals("Los Angeles Time", losAngeles.localizedName(TimeZoneNameStyle.LOCATION, locale = EN))
    }

    @Test
    fun exemplarCitiesAreLocalized() {
        assertEquals("Los Angeles", (zoneOrNull("America/Los_Angeles") ?: return).exemplarCity(EN))
        assertEquals("Dubaj", (zoneOrNull("Asia/Dubai") ?: return).exemplarCity(CS))
        assertEquals("Praha", (zoneOrNull("Europe/Prague") ?: return).exemplarCity(CS))
        assertTrue((zoneOrNull("Europe/Lisbon") ?: return).exemplarCity(PT).isNotBlank())
    }

    @Test
    fun anUnknownZoneStillAnswers() {
        // The fallback ladder ends at the identifier rather than at nothing.
        val zone = zoneOrNull("Etc/GMT+5") ?: return
        assertTrue(zone.displayName(locale = EN).isNotBlank())
    }

    @Test
    fun everyLocaleHasTheOffsetFormat() {
        var checked = 0
        for (locale in CldrTimeZone.supportedLocales) {
            assertTrue(UtcOffset(hours = -3).displayName(locale).isNotBlank(), "$locale")
            checked++
        }
        assertTrue(checked > 1000, "expected the full locale set, got $checked")
    }
}
