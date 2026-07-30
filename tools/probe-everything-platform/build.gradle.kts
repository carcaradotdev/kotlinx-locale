plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    budgetBytes = 54 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-country-platform"))
        implementation(project(":kotlinx-locale-currency-platform"))
        implementation(project(":kotlinx-locale-datetime-platform"))
        implementation(project(":kotlinx-locale-types"))
    }
}
