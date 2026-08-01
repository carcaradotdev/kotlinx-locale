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

val published = listOf(
    "kotlinx-locale-core",
    "kotlinx-locale-types",
    "kotlinx-locale-platform",
    "kotlinx-locale-serialization",
    "kotlinx-locale-country-types",
    "kotlinx-locale-country-core",
    "kotlinx-locale-country-cldr-runtime",
    "kotlinx-locale-country-cldr-full",
    "kotlinx-locale-country-platform",
    "kotlinx-locale-country-serialization",
    "kotlinx-locale-language-core",
    "kotlinx-locale-language-cldr-runtime",
    "kotlinx-locale-language-cldr-full",
    "kotlinx-locale-number-core",
    "kotlinx-locale-number-cldr-runtime",
    "kotlinx-locale-number-cldr-full",
    "kotlinx-locale-currency-types",
    "kotlinx-locale-currency-core",
    "kotlinx-locale-currency-cldr-runtime",
    "kotlinx-locale-currency-cldr-full",
    "kotlinx-locale-currency-platform",
    "kotlinx-locale-currency-serialization",
    "kotlinx-locale-datetime-core",
    "kotlinx-locale-datetime-cldr-runtime",
    "kotlinx-locale-datetime-cldr-full",
    "kotlinx-locale-datetime-cldr-skeletons",
    "kotlinx-locale-datetime-cldr-relative",
    "kotlinx-locale-datetime-platform",
    "kotlinx-locale-timezone-core",
    "kotlinx-locale-timezone-cldr-runtime",
    "kotlinx-locale-timezone-cldr-full",
    "kotlinx-locale-timezone-cldr-cities",
    "kotlinx-locale-phone-core",
    "kotlinx-locale-phone-metadata-runtime",
    "kotlinx-locale-phone-metadata-full",
    "kotlinx-locale-phone-serialization",
    "kotlinx-locale-codegen-emitters",
    "kotlinx-locale-codegen-data",
    "kotlinx-locale-gradle-plugin",
)

// No published artifact name may be a strict prefix of another at a hyphen
// boundary. Kotlin Multiplatform already owns that suffix space: each module
// publishes a root module plus one artifact per target, so on Maven Central
// `<name>-jvm` and `<name>-iosarm64` sit in the same listing as `<name>`. A
// second library named `<name>-format` lands in that listing too, and nothing in
// the coordinate tells a reader — or a tool matching on target suffixes — which
// of them is a platform variant. Checked here because this is where the names
// are, and at configuration time so a rename cannot reach `check` uncaught.
val collisions = published.flatMap { prefix ->
    published.filter { it.startsWith("$prefix-") }.map { "$prefix is a prefix of $it" }
}
require(collisions.isEmpty()) {
    collisions.joinToString(
        prefix = "Published artifact names collide with the Kotlin Multiplatform target suffixes:\n  ",
        separator = "\n  ",
    )
}

// The project name follows the directory, so nothing has to be renamed here.
published.forEach { include(":$it") }

// Not published, and so absent from the check above and from the prefix. The
// conformance suite is for this build's own test source sets, and :codegen is the
// extraction half of code generation, which clones repositories and parses XML.
include(":conformance-test-suite")
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
