/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    "kotlinx-locale-territory-cldr-full",
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
    "kotlinx-locale-currency-cldr-plurals",
    "kotlinx-locale-currency-platform",
    "kotlinx-locale-currency-serialization",
    "kotlinx-locale-datetime-core",
    "kotlinx-locale-datetime-cldr-runtime",
    "kotlinx-locale-datetime-cldr-full",
    "kotlinx-locale-datetime-cldr-skeletons",
    "kotlinx-locale-datetime-cldr-relative",
    "kotlinx-locale-datetime-cldr-intervals",
    "kotlinx-locale-datetime-cldr-durations",
    "kotlinx-locale-datetime-platform",
    "kotlinx-locale-timezone-core",
    "kotlinx-locale-timezone-cldr-runtime",
    "kotlinx-locale-timezone-cldr-full",
    "kotlinx-locale-timezone-cldr-cities",
    "kotlinx-locale-personname-core",
    "kotlinx-locale-personname-cldr-runtime",
    "kotlinx-locale-personname-cldr-full",
    "kotlinx-locale-phone-core",
    "kotlinx-locale-phone-metadata-runtime",
    "kotlinx-locale-phone-metadata-full",
    "kotlinx-locale-phone-serialization",
    "kotlinx-locale-codegen-pipeline",
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

// The assertion vocabulary every test source set in this build is written in.
// Not kotlin-test, because kotlin-test and the test framework cannot share a
// Kotlin/Wasm compilation; see the module's own build file.
include(":test-assertions")

// Measures what the generated data weighs, per platform. Not published, and not
// on any consumer classpath: it reads this checkout and reports on it.
include(":kotlinx-locale-size-report")

// The live ICU comparison. A plain JVM module, because ICU4J is a JVM library
// and this is the one place in the build allowed to depend on it at test time.
//
// It needs the ICU4J jar and nothing else: no CLDR clone, which is what lets it
// run in CI, where `codegen/repos` never exists. What it compares is every
// shipped table against the answers ICU gives for the same question, across all
// eleven hundred locales rather than the thirty a committed golden can afford.
// The disagreements that are real and expected live in `conformance/ledger`.
include(":conformance-icu")

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
