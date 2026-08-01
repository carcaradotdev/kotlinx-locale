plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 145.1 KB. It was 112.7 KB before the stand-alone calendar
    // names landed, and they are here rather than in an artifact of their own
    // because the pattern renderer needs them: 110 of CLDR's availableFormats
    // patterns use the stand-alone month letter, and without the table those
    // render the wrong grammatical case.
    budgetBytes = 165 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-datetime-cldr-full"))
    }
}
