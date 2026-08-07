package dev.carcara.kotlinx.locale.datetime

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToDateTimeFormats
import dev.carcara.kotlinx.locale.datetime.cldr.CldrDateTime
import dev.carcara.kotlinx.locale.datetime.cldr.conformance.assertMatchesIcuCalendarNames

/**
 * Month and weekday names are held to ICU exactly. The patterns behind the
 * formatted output are cross-checked separately in `IcuGolden`, which can reach
 * the tables directly because it lives in the module that owns them.
 */
val CldrDateTimeConformanceTest by matrixSuite {

    test("conforms to the source contract at the exact tier") {
        CldrDateTime.assertConformsToDateTimeFormats(ConformanceTier.EXACT)
    }

    test("month and weekday names match ICU") {
        CldrDateTime.assertMatchesIcuCalendarNames()
    }
}
