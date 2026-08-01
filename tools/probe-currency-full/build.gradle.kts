plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 456.1 KB, up from 370 KB. Money now formats through the
    // shared number engine, so this pulls in the number symbol and pattern
    // tables, the plural rules that compact money is keyed by, and the compact
    // money table itself. A consumer who wants only names and standard
    // formatting pays for the plural rules, which are four kilobytes.
    budgetBytes = 500 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-currency-cldr-full"))
    }
}
