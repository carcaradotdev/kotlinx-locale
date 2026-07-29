plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    budgetBytes = 20 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-country-core"))
    }
}
