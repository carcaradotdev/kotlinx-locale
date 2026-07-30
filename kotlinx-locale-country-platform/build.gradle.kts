// Country names from the host platform rather than from bundled CLDR data.
//
// Note what is absent: no dependency on -cldr-full or -cldr-runtime. That is
// the point of the layering. A build that takes this module ships no name
// tables at all.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-country-core"))
            api(project(":kotlinx-locale-platform"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":conformance-test-suite"))
            // To compose with, and to compare against, on the targets that have
            // no platform data of their own.
            implementation(project(":kotlinx-locale-country-cldr-full"))
        }
    }
}
