// Every territory libphonenumber describes, as generated tables.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-phone-metadata-runtime"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":conformance-test-suite"))
        }
    }
}
