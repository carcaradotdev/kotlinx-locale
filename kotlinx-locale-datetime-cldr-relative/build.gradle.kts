// "3 days ago" and its wording in every locale.
//
// Its own artifact rather than part of -cldr-full: relative wording needs no
// date patterns and no month names, so a consumer who only wants this should not
// carry them. The table is also larger than the pattern table and larger than
// all three skeleton tables together.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-datetime-cldr-runtime"))
            // The plural rules that pick the wording and the formatter that
            // renders its count.
            api(project(":kotlinx-locale-number-cldr-full"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
