// The formatting algorithm over CLDR-shaped person name records: pattern
// selection, field modifiers, the empty-field cleanup and the space
// replacement. Carries no records; the table is a constructor argument, so a
// build narrowed through the Gradle plugin uses the same code as the full one.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-personname-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
