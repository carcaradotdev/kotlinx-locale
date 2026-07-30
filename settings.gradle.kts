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
//
// A published module's directory is its artifact name, the way kotlinx-coroutines
// lays its modules out. It costs a long prefix on twenty directories and buys two
// things: a listing of the repository root reads like a listing on Maven Central,
// and carrying the prefix is what publishing means here, so a directory without
// one ships to nobody. There is also no directory-to-artifact mapping left to
// keep in sync, because the two are one string.
val published = listOf(
    "kotlinx-locale-core",
    "kotlinx-locale-types",
    "kotlinx-locale-platform",
    "kotlinx-locale-country-types",
    "kotlinx-locale-country-core",
    "kotlinx-locale-country-cldr-runtime",
    "kotlinx-locale-country-cldr-full",
    "kotlinx-locale-country-platform",
    "kotlinx-locale-currency-types",
    "kotlinx-locale-currency-core",
    "kotlinx-locale-currency-cldr-runtime",
    "kotlinx-locale-currency-cldr-full",
    "kotlinx-locale-currency-platform",
    "kotlinx-locale-datetime-core",
    "kotlinx-locale-datetime-cldr-runtime",
    "kotlinx-locale-datetime-cldr-full",
    "kotlinx-locale-datetime-cldr-skeletons",
    "kotlinx-locale-datetime-platform",
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
