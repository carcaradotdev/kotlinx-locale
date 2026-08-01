plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 154.9 KB. Most of it is the wording itself, which is larger
    // than the date patterns; the number and plural tables it formats counts
    // through are the rest.
    budgetBytes = 190 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-datetime-cldr-relative"))
    }
}
