plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-language-cldr-runtime"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
