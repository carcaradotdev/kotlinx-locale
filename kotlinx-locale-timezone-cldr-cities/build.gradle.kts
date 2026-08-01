// The exemplar cities, in their own artifact.
//
// 38,323 names across the locales that declare them, which is the largest table
// in the library after the language names. Everything except the generic
// location format works without them, and that format's own fallback is the one
// UTS #35 prescribes, so this is worth asking for deliberately.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-timezone-cldr-full"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":conformance-test-suite"))
            implementation(project(":kotlinx-locale-country-cldr-full"))
            implementation(project(":kotlinx-locale-number-cldr-full"))
        }
    }
}
