pluginManagement {
    // The convention plugins live in an included build rather than in buildSrc:
    // a buildSrc change invalidates every task in the build, an included build
    // only invalidates the consumers of the plugin that changed.
    includeBuild("build-logic")

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
    "locale-platform" to "kotlinx-locale-platform",
    "country-types" to "kotlinx-locale-country-types",
    "country-core" to "kotlinx-locale-country-core",
    "country-cldr-format" to "kotlinx-locale-country-cldr-format",
    "country-cldr" to "kotlinx-locale-country-cldr",
    "country-platform" to "kotlinx-locale-country-platform",
    "currency-types" to "kotlinx-locale-currency-types",
    "currency-core" to "kotlinx-locale-currency-core",
    "currency-cldr-format" to "kotlinx-locale-currency-cldr-format",
    "currency-cldr" to "kotlinx-locale-currency-cldr",
    "currency-platform" to "kotlinx-locale-currency-platform",
    "datetime-core" to "kotlinx-locale-datetime-core",
    "datetime-cldr-format" to "kotlinx-locale-datetime-cldr-format",
    "datetime-cldr" to "kotlinx-locale-datetime-cldr",
).forEach { (dir, artifact) ->
    include(":$dir")
    project(":$dir").name = artifact
}

include(":cldr-data")
project(":cldr-data").name = "kotlinx-locale-cldr-data"

include(":codegen-api")
project(":codegen-api").name = "kotlinx-locale-codegen"

include(":conformance")
project(":conformance").name = "kotlinx-locale-conformance"

include(":gradle-plugin")
project(":gradle-plugin").name = "kotlinx-locale-gradle-plugin"

include(":codegen")

// Kotlin/JS probes that measure what each dependency set costs a consumer. Not
// published; see tools/README.md. The list lives in one file that both this and
// the verification convention plugin read, so a probe cannot be built without
// also being reported on.
providers
    .fileContents(layout.rootDirectory.file("gradle/size-probes.txt"))
    .asText
    .get()
    .lineSequence()
    .map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .forEach { probe -> include(":tools:$probe") }
