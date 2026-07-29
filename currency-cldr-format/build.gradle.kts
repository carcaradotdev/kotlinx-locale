// The CLDR record format for currency symbols and names, plus the pattern-based
// number formatter and parser. No records: those come from -cldr or from a
// generated source set.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-currency-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
