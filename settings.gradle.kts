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
// -core is the hand-written contract, -cldr-runtime is the engine that reads
// CLDR-shaped tables, and -cldr-full is that engine plus every locale CLDR has.
// The locale domain is the root, so it carries no domain segment. Anything named
// codegen-* runs at build time and never belongs on an application classpath.
// Directory names drop the shared kotlinx-locale prefix.
val published = listOf(
    "locale-core" to "kotlinx-locale-core",
    "locale-types" to "kotlinx-locale-types",
    "locale-platform" to "kotlinx-locale-platform",
    "country-types" to "kotlinx-locale-country-types",
    "country-core" to "kotlinx-locale-country-core",
    "country-cldr-runtime" to "kotlinx-locale-country-cldr-runtime",
    "country-cldr-full" to "kotlinx-locale-country-cldr-full",
    "country-platform" to "kotlinx-locale-country-platform",
    "currency-types" to "kotlinx-locale-currency-types",
    "currency-core" to "kotlinx-locale-currency-core",
    "currency-cldr-runtime" to "kotlinx-locale-currency-cldr-runtime",
    "currency-cldr-full" to "kotlinx-locale-currency-cldr-full",
    "currency-platform" to "kotlinx-locale-currency-platform",
    "datetime-core" to "kotlinx-locale-datetime-core",
    "datetime-cldr-runtime" to "kotlinx-locale-datetime-cldr-runtime",
    "datetime-cldr-full" to "kotlinx-locale-datetime-cldr-full",
    "datetime-platform" to "kotlinx-locale-datetime-platform",
    "codegen-emitters" to "kotlinx-locale-codegen-emitters",
    "codegen-data" to "kotlinx-locale-codegen-data",
    "gradle-plugin" to "kotlinx-locale-gradle-plugin",
)

// No published artifact name may be a strict prefix of another at a hyphen
// boundary. Kotlin Multiplatform already owns that suffix space: each module
// publishes a root module plus one artifact per target, so on Maven Central
// `<name>-jvm` and `<name>-iosarm64` sit in the same listing as `<name>`. A
// second library named `<name>-format` lands in that listing too, and nothing in
// the coordinate tells a reader — or a tool matching on target suffixes — which
// of them is a platform variant. Checked here because this is where the names
// are, and at configuration time so a rename cannot reach `check` uncaught.
val collisions = published.map { (_, artifact) -> artifact }.let { artifacts ->
    artifacts.flatMap { prefix ->
        artifacts.filter { it.startsWith("$prefix-") }.map { "$prefix is a prefix of $it" }
    }
}
require(collisions.isEmpty()) {
    collisions.joinToString(
        prefix = "Published artifact names collide with the Kotlin Multiplatform target suffixes:\n  ",
        separator = "\n  ",
    )
}

published.forEach { (dir, artifact) ->
    include(":$dir")
    project(":$dir").name = artifact
}

// Not published, and so absent from the check above. The conformance suite is
// for this build's own test source sets, and :codegen is the extraction half of
// code generation, which clones repositories and parses XML.
include(":conformance-test-suite")
project(":conformance-test-suite").name = "kotlinx-locale-conformance-test-suite"

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
