plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-currency-cldr-format"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":kotlinx-locale-conformance"))
        }
    }
}
