package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToIcuSkeletons
import dev.carcara.kotlinx.locale.conformance.assertMatchesCldrDateTimeCases
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.CldrDateTimeSkeletons
import kotlin.test.Test

class SkeletonConformanceTest {

    @Test
    fun conformsToIcu() {
        CldrDateTimeSkeletons.assertConformsToIcuSkeletons(ConformanceTier.EXACT)
    }

    @Test
    fun matchesCldrOwnCases() {
        CldrDateTimeSkeletons.assertMatchesCldrDateTimeCases()
    }
}
