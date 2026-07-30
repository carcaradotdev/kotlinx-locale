plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-core"))
            api(project(":kotlinx-locale-country-types"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
