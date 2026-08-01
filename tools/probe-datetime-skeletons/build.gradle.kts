plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 206.9 KB. It tracks datetime-full, which grew by the
    // stand-alone calendar names; the skeleton tables themselves are unchanged.
    budgetBytes = 230 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-datetime-cldr-skeletons"))
    }
}
