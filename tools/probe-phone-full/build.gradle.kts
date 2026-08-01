plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured on the first run; see docs/size.md for the current figure.
    budgetBytes = 110 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-phone-metadata-full"))
    }
}
