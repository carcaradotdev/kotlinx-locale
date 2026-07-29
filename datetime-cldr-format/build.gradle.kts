// The CLDR record format for dates and times, plus the pattern parser and
// formatter. No records: those come from -cldr or from a generated source set.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-datetime-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
