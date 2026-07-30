plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    budgetBytes = 130 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-datetime-cldr-full"))
    }
}
