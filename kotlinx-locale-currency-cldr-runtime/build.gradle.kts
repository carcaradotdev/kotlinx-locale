// The code that operates on CLDR-shaped currency data: the record reader, plus
// the pattern-based number formatter and parser. No records — those come from
// -cldr-full or from a plugin-generated source set.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-currency-core"))
            api(project(":kotlinx-locale-number-cldr-runtime"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
