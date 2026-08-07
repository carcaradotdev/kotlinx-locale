// Everything the root project does lives in the convention plugin, so this file
// stays a declaration of what the root is rather than a place logic accumulates.
// Shared configuration reaches the modules through the convention plugins they
// apply, never through allprojects or subprojects.
plugins {
    id("kotlinx-locale-verification")
    // Coverage aggregation is a property of the whole build rather than of any
    // module, and Kover merges through dependencies declared at the root. This
    // is the one thing the root owns beyond verification.
    id("kotlinx-locale-coverage")
}

/**
 * What the coverage number is about: the published library.
 *
 * The build-time modules are left out on purpose. `:codegen` clones repositories
 * and parses XML, `:conformance-icu` is a test harness, `:test-assertions` and
 * `:conformance-test-suite` are test infrastructure, and `tools/probe-*` are
 * size probes with no code. Including any of them would move the headline number
 * without telling a reader anything about the artifacts people depend on.
 *
 * They still produce their own reports; they are simply not what "coverage of
 * the library" means.
 */
val notTheLibrary = setOf(
    ":codegen",
    ":conformance-icu",
    ":conformance-test-suite",
    ":test-assertions",
    ":kotlinx-locale-codegen-data",
    ":kotlinx-locale-codegen-emitters",
    ":kotlinx-locale-gradle-plugin",
)

dependencies {
    for (module in rootProject.subprojects) {
        // `:tools` itself is the container Gradle creates for `:tools:probe-*`.
        // It has no build file and so no Kover variant to aggregate.
        if (module.path == ":tools" || module.path.startsWith(":tools:")) continue
        if (module.path in notTheLibrary) continue
        kover(project(module.path))
    }
}
