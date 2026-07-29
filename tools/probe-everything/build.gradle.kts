plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    budgetBytes = 900 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-country-cldr"))
        implementation(project(":kotlinx-locale-currency-cldr"))
        implementation(project(":kotlinx-locale-datetime-cldr"))
        implementation(project(":kotlinx-locale-types"))
    }
}
