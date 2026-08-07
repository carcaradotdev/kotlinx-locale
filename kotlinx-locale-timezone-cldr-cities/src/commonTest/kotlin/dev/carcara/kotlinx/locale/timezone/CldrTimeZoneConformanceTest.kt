@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.timezone

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.country.cldr.CldrCountry
import dev.carcara.kotlinx.locale.number.cldr.CldrNumber
import dev.carcara.kotlinx.locale.timezone.cldr.CldrTimeZone
import dev.carcara.kotlinx.locale.timezone.cldr.cities.internal.data.timeZoneCitiesRegistry
import dev.carcara.kotlinx.locale.timezone.cldr.runtime.PayloadTimeZoneNames
import dev.carcara.kotlinx.locale.timezone.conformance.assertConformsToIcuTimeZoneNames

/**
 * Held against ICU with every table this domain can be given.
 *
 * Composed by hand rather than using `CldrTimeZoneCities`, because the country
 * names and the number symbols are the two things that object deliberately does
 * not reach for: naming a zone must not drag four hundred kilobytes of country
 * tables into a build that only wanted `Pacific Standard Time`. This is the
 * other end of that choice, where a build did ask for them, and it is the
 * configuration the generic location format is written for.
 */
val CldrTimeZoneConformanceTest by matrixSuite {

    val source = PayloadTimeZoneNames(
        CldrTimeZone.formatRecords,
        CldrTimeZone.nameRecords,
        timeZoneCitiesRegistry,
        CldrTimeZone.metadata,
        CldrNumber,
    ) { region, locale -> CldrCountry.countryNameOrNull(region, locale) }

    test("names agree with ICU") {
        source.assertConformsToIcuTimeZoneNames()
    }
}
