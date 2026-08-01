plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 982.8 KB with the country, currency, datetime and locale
    // catalog artifacts. Deliberately not every artifact in the library: the
    // language names alone are larger than all of these together, and the
    // exemplar cities are larger again, so folding them in would make this row
    // a number nobody's build resembles.
    budgetBytes = 1100 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-country-cldr-full"))
        implementation(project(":kotlinx-locale-currency-cldr-full"))
        implementation(project(":kotlinx-locale-datetime-cldr-full"))
        implementation(project(":kotlinx-locale-types"))
    }
}
