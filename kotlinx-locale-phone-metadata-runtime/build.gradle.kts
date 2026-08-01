// The code that operates on libphonenumber-shaped data: the bounded pattern
// matcher, the parser, the validator and the formatters. No metadata; that
// comes from -metadata-full or from a plugin-generated source set.
//
// The matcher is why this domain is pure common Kotlin. libphonenumber
// validates with regular expressions, and Kotlin's `Regex` delegates to a
// different engine on every target, so using it would mean a number that
// validates on Android and not on JS. The patterns turn out to need only
// alternation, character classes, `\d`, groups, bounded repetition and an end
// anchor, so this module evaluates that subset itself and generation fails if
// a later libphonenumber release steps outside it.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-phone-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
