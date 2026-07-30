// The code that reads CLDR-shaped country name records, and none of the records.
//
// The table arrives as a constructor argument, which is what lets -cldr-full
// pass 1121 locales and a plugin-narrowed build pass three with identical lookup
// semantics. A platform source depends on neither.
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
