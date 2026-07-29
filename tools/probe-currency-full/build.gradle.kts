plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    budgetBytes = 370 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-currency-cldr"))
    }
}
