plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-country-core"))
            api(project(":kotlinx-locale-currency-core"))
            api(project(":kotlinx-locale-datetime-core"))
            // kotlin-test is an api dependency on purpose: the suite reports
            // through assertions, so a caller is already in a test source set.
            api(libs.kotlin.test)
        }
    }
}
