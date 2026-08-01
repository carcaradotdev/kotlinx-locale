// The code that operates on CLDR-shaped number data: the symbol record reader,
// the pattern engine, the compact-notation algorithm, the plural-rule evaluator
// and the ordinal rules. No records — those come from -cldr-full or from a
// plugin-generated source set.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-number-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
