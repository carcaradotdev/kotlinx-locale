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
