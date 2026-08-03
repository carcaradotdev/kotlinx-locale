plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 39.8 KB. The patterns dedupe hard: a hundred and forty-seven
    // distinct records cover all 1122 locales, which is why a domain with
    // forty-two cells per locale costs less than the country names.
    budgetBytes = 60 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-personname-cldr-full"))
    }
}
