plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Larger than the other platform probes because kotlinx-datetime itself is in
    // here, as it is in probe-datetime-full. The formatting is the host's; the
    // LocalDate arithmetic is still code that ships.
    budgetBytes = 42 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-datetime-platform"))
    }
}
