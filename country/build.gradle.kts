plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
