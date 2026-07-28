plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale"))
            api(project(":kotlinx-locale-country"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
