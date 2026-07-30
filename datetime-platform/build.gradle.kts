// Date and time formatting from the host platform rather than from bundled CLDR
// patterns. No dependency on -cldr or -cldr-format.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-datetime-core"))
            api(project(":kotlinx-locale-platform"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":kotlinx-locale-conformance"))
            implementation(project(":kotlinx-locale-datetime-cldr"))
        }
    }
}
