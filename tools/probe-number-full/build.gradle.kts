plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 77.1 KB: the symbols, the decimal and percent patterns, the
    // two compact tables, the plural rules and the ordinal rule closures.
    budgetBytes = 110 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-number-cldr-full"))
    }
}
