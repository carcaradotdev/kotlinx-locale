// The skeleton tables: what a locale's availableFormats, appendItems and quarter
// names resolve to, for every locale CLDR has. Opt in, and its own artifact,
// because the tables are around 210 KB of raw payload against the 435 KB the
// whole of -cldr-full weighs — folding them in would make every consumer of
// ordinary date formatting pay for skeletons.
//
// The matcher is in -cldr-runtime rather than here, for the same reason the
// pattern formatter is: a build narrowed to three locales through the Gradle
// plugin generates its own tables and still needs the algorithm.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api, not implementation: the binding object exposes SkeletonFormatSource,
            // and reads its patterns through CldrDateTime.
            api(project(":kotlinx-locale-datetime-cldr-full"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":conformance-test-suite"))
        }
    }
}
