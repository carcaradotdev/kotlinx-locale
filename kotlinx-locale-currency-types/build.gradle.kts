plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Only for the InternalKotlinxLocaleApi marker on the country-to-currency map.
            api(project(":kotlinx-locale-core"))
        }
    }
}
