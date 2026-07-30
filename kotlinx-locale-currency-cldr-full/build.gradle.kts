plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-currency-cldr-runtime"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":conformance-test-suite"))
        }
    }
}
