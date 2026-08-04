plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 117.3 KB. Most of it is the wording, and the number and plural
    // tables it renders counts through are the rest. Smaller than the relative
    // probe next door because fourteen units of seven slots is less than eight
    // units of eighteen.
    budgetBytes = 150 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-datetime-cldr-durations"))
    }
}
