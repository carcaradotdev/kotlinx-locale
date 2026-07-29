plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    budgetBytes = 30 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-currency-core"))
    }
}
