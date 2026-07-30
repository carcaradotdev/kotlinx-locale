// What the host platform can tell us about locales, before any domain is
// involved: whether it exposes locale data at all, and which locales it
// enumerates. Shared by the three domain platform modules so that the nine
// per-target implementations exist once rather than three times.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
