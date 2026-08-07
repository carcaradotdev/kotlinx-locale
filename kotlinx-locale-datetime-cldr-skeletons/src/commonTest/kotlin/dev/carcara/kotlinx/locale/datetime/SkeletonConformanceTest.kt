package dev.carcara.kotlinx.locale.datetime

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.CldrDateTimeSkeletons
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.conformance.assertConformsToIcuSkeletons
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.conformance.assertMatchesCldrDateTimeCases

val SkeletonConformanceTest by matrixSuite {

    test("conforms to ICU") {
        CldrDateTimeSkeletons.assertConformsToIcuSkeletons(ConformanceTier.EXACT)
    }

    test("matches CLDR's own cases") {
        CldrDateTimeSkeletons.assertMatchesCldrDateTimeCases()
    }
}
