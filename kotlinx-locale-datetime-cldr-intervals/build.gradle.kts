// The interval tables: each locale's pattern per skeleton per greatest-difference
// field, which is what turns two dates into "Jul 18 – 22, 2026" rather than into
// two whole dates with a dash between them. Opt in, and its own artifact, because
// it is around 75 patterns per locale that only a consumer formatting ranges
// needs.
//
// It depends on the skeletons rather than on -cldr-full because an interval is a
// split of the pattern the skeleton matcher picks, and the two share one matcher
// per locale. The splitting itself is in -cldr-runtime, for the same reason the
// matcher is: a build narrowed through the Gradle plugin generates its own tables
// and still needs the algorithm.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api, not implementation: the binding object exposes IntervalFormatSource,
            // and reads its matcher through CldrDateTimeSkeletons.
            api(project(":kotlinx-locale-datetime-cldr-skeletons"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":conformance-test-suite"))
        }
    }
}
