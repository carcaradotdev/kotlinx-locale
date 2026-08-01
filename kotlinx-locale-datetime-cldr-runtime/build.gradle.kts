// The code that operates on CLDR-shaped date and time data: the record reader,
// plus the pattern parser and formatter. No records — those come from -cldr-full
// or from a plugin-generated source set.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-datetime-core"))
            // The interfaces only. Relative wording picks a plural form and renders
            // its count, and the tables for both come from the consumer.
            api(project(":kotlinx-locale-number-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
