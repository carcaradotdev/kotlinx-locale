plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 1291.1 KB, the largest artifact in the library by a wide
    // margin: every language, script and region name in every locale. This is
    // the strongest argument for the Gradle plugin in the whole project, since
    // a language picker needs a handful of names rather than eleven hundred
    // locales' worth.
    budgetBytes = 1400 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-language-cldr-full"))
    }
}
