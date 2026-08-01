// The ICU fixtures and the assertions over them, for this build's own test
// source sets.
//
// Not published, which is why it applies the base convention plugin rather than
// `kotlinx-locale-multiplatform`: no publication, and no committed ABI dump for
// an ABI nobody outside this build compiles against. The six modules that use it
// take it as a project dependency in commonTest.
plugins {
    id("kotlinx-locale-multiplatform-base")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-country-core"))
            api(project(":kotlinx-locale-currency-core"))
            api(project(":kotlinx-locale-datetime-core"))
            // The skeleton contract lives in -cldr-runtime rather than -core,
            // because no platform source can answer it. The fixtures for it
            // therefore need the runtime module, not just the core one.
            api(project(":kotlinx-locale-datetime-cldr-runtime"))
            api(project(":kotlinx-locale-number-core"))
            api(project(":kotlinx-locale-timezone-core"))
            // kotlin-test is an api dependency on purpose: the suite reports
            // through assertions, so a caller is already in a test source set.
            api(libs.kotlin.test)
        }
        // Declared again for this module's own tests rather than inherited from
        // the api above. The Android host test compilation does not pick it up
        // that way, so `check` failed there on an unresolved `Test` annotation
        // while every other target compiled.
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
