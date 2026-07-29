plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-core"))
            api(project(":kotlinx-locale-currency-types"))
            api(project(":kotlinx-locale-country-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
