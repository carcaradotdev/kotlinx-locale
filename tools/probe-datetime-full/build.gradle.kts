plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 124.1 KB, up from 112.7 KB when the stand-alone calendar
    // names landed. They are here rather than in an artifact of their own
    // because the pattern renderer needs them: 110 of CLDR's availableFormats
    // patterns use the stand-alone month letter, and without the table those
    // render the wrong grammatical case.
    budgetBytes = 140 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-datetime-cldr-full"))
    }
}
