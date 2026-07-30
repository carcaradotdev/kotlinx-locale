plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-country-cldr-runtime"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":kotlinx-locale-conformance-test-suite"))
        }
    }
}
