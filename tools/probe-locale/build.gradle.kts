plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    budgetBytes = 18 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-core"))
    }
}
