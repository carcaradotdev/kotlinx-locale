// The phone number value type, the source contracts and the option enums.
//
// The data behind them is Google's libphonenumber rather than CLDR, which is
// why the layer below is named for it: the numbering plans are ITU-T E.164 and
// libphonenumber is the machine-readable form of them the industry maintains.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-core"))
            api(project(":kotlinx-locale-country-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
