// Currency symbols, names and number formatting from the host platform rather
// than from bundled CLDR tables. No dependency on -cldr or -cldr-format.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-currency-core"))
            api(project(":kotlinx-locale-platform"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":kotlinx-locale-conformance"))
            // To compose with, and to fall back to on the targets that have no
            // platform data of their own.
            implementation(project(":kotlinx-locale-currency-cldr"))
        }
    }
}
