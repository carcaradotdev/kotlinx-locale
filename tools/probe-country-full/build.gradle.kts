plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    budgetBytes = 460 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-country-cldr"))
    }
}
