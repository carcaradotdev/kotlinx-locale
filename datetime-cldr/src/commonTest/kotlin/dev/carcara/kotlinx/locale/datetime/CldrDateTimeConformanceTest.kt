package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToDateTimeFormats
import dev.carcara.kotlinx.locale.datetime.cldr.CldrDateTime
import kotlin.test.Test

/**
 * Month and weekday names are held to ICU exactly. The patterns behind the
 * formatted output are cross-checked separately in [IcuGoldenTest], which can
 * reach the tables directly because it lives in the module that owns them.
 */
class CldrDateTimeConformanceTest {

    @Test
    fun conformsExactly() = CldrDateTime.assertConformsToDateTimeFormats(ConformanceTier.EXACT)
}
