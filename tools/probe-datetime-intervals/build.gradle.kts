plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 228.5 KB, most of which is the skeletons this depends on.
    // Pairs with probe-datetime-skeletons: the difference between the two is
    // what the interval tables themselves cost.
    budgetBytes = 280 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-datetime-cldr-intervals"))
    }
}
