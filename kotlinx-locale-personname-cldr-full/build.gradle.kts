// The generated person name tables for every locale CLDR has, plus the binding
// object and its entry points.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-personname-cldr-runtime"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
