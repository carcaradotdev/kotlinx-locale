plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // The same calls as probe-country-full, answered by the host instead of by
    // bundled tables. The budget is deliberately close to probe-country-codes:
    // if this ever approaches probe-country-full, something pulled CLDR data
    // back into the platform path.
    budgetBytes = 25 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-country-platform"))
    }
}
