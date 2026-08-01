// The code that turns CLDR-shaped zone data into a name: the localized GMT
// format, metazone resolution and the fallback ladder of UTS #35 Part 4. No
// records — those come from -cldr-full and -cldr-cities.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-timezone-core"))
            // The locale's own digits for the GMT offset.
            api(project(":kotlinx-locale-number-cldr-runtime"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
