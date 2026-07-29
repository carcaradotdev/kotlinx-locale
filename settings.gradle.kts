pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Not FAIL_ON_PROJECT_REPOS: the Kotlin Gradle plugin injects project-level ivy
    // repositories (nodejs.org, Binaryen) to provision the JS/Wasm toolchains.
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
    }
}

rootProject.name = "kotlinx-locale-project"

// Artifacts are kotlinx-locale[-<domain>]-<layer>: -types is generator output,
// -core is the hand-written contract, -cldr is one implementation of it. The
// locale domain is the root, so it carries no domain segment. Directory names
// drop the shared kotlinx-locale prefix.
listOf(
    "locale-core" to "kotlinx-locale-core",
    "locale-types" to "kotlinx-locale-types",
    "country-types" to "kotlinx-locale-country-types",
    "country-core" to "kotlinx-locale-country-core",
    "country-cldr" to "kotlinx-locale-country-cldr",
    "currency-types" to "kotlinx-locale-currency-types",
    "currency-core" to "kotlinx-locale-currency-core",
    "currency-cldr" to "kotlinx-locale-currency-cldr",
    "datetime-core" to "kotlinx-locale-datetime-core",
    "datetime-cldr" to "kotlinx-locale-datetime-cldr",
).forEach { (dir, artifact) ->
    include(":$dir")
    project(":$dir").name = artifact
}

include(":codegen")
