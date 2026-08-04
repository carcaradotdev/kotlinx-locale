plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 811.1 KB, against 442.8 KB for the currency artifact this one
    // sits on top of. It carries that artifact whole, because the count-less
    // display name is the third step of the fallback chain, so the difference
    // between the two rows is what the count-keyed names cost.
    budgetBytes = 900 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-currency-cldr-plurals"))
    }
}
