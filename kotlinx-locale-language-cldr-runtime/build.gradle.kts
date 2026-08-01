// The lookup over CLDR-shaped locale display name records. No records — those
// come from -cldr-full or from a plugin-generated source set.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-language-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
