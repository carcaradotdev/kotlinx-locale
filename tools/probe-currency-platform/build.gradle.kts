plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Below probe-currency-codes, which is not an error: that probe calls
    // Country.currency and so carries the country-to-currency table and the
    // Country enum, neither of which this one touches. The comparison that means
    // something is probe-currency-full, which makes these exact calls.
    budgetBytes = 26 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-currency-platform"))
    }
}
