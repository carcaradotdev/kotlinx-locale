plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 172.6 KB, with the same headroom the datetime-full row keeps.
    // The gap between the two rows is the whole reason this is a separate
    // artifact: folding the tables into -cldr-full would put every consumer of
    // ordinary date formatting 60 KB over its ceiling.
    budgetBytes = 200 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-datetime-cldr-skeletons"))
    }
}
