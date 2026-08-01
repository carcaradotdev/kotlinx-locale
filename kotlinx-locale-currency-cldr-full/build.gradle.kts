plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-currency-cldr-runtime"))
            // Compact money is keyed by plural category, so the rules that select
            // from the table travel with it.
            api(project(":kotlinx-locale-number-cldr-full"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":conformance-test-suite"))
        }
    }
}
