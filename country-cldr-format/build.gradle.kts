// The CLDR record format for country names, without the records.
//
// Split from -cldr so that a build generating its own narrowed tables can bind
// them to CountryNameSource without also depending on the full CLDR data, and so
// that a platform source depends on neither.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-country-core"))
        }
    }
}
