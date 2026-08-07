// The live ICU comparison, and the only module in this build that puts ICU4J on
// a test classpath.
//
// Everywhere else ICU is a build-time input to `:codegen`, which turns it into
// committed Kotlin goldens that run on all twenty-four test targets. Those
// goldens cover thirty locales, because a golden wide enough for eleven hundred
// is megabytes of source in every native test binary. This module answers the
// other half of the question: call ICU directly, on the JVM, for every locale
// the library ships, and store nothing.
//
// Not published, so it is absent from the `published` list in settings.gradle.kts.
plugins {
    id("kotlinx-locale-jvm")
}

dependencies {
    testImplementation(libs.icu4j)
    testImplementation(libs.libphonenumber)

    // For `Digest`, which the digest generator writes with and the other
    // targets read back. Shared so that the two halves cannot drift.
    testImplementation(project(":conformance-test-suite"))

    // Every source with a table to compare. These resolve to the `jvm` variant
    // of each multiplatform module.
    testImplementation(project(":kotlinx-locale-core"))
    testImplementation(project(":kotlinx-locale-country-core"))
    testImplementation(project(":kotlinx-locale-country-cldr-full"))
    testImplementation(project(":kotlinx-locale-currency-core"))
    testImplementation(project(":kotlinx-locale-currency-cldr-full"))
    testImplementation(project(":kotlinx-locale-currency-cldr-plurals"))
    testImplementation(project(":kotlinx-locale-language-cldr-full"))
    testImplementation(project(":kotlinx-locale-number-cldr-full"))
    testImplementation(project(":kotlinx-locale-datetime-cldr-full"))
    testImplementation(project(":kotlinx-locale-datetime-cldr-skeletons"))
    testImplementation(project(":kotlinx-locale-datetime-cldr-relative"))
    testImplementation(project(":kotlinx-locale-datetime-cldr-durations"))
    testImplementation(project(":kotlinx-locale-timezone-cldr-full"))
    testImplementation(project(":kotlinx-locale-timezone-cldr-cities"))
    testImplementation(project(":kotlinx-locale-personname-cldr-full"))
    testImplementation(project(":kotlinx-locale-phone-metadata-full"))
}

tasks.withType<Test>().configureEach {
    // The one place in the build that raises the 1 GB ceiling the JVM
    // convention plugin sets. ICU caches a resource bundle per locale and never
    // evicts within a run, and this asks about eleven hundred locales across the
    // currency, region, locale-display and zone bundles.
    //
    // The three Test tasks here are never concurrent: `test` runs in CI,
    // `updateLedger` and `updateDigests` are run by hand when a pin moves.
    maxHeapSize = "3g"

    // The ledger is read from and written to the repository root, which a test
    // has no other way to find.
    systemProperty("kotlinx.locale.rootDir", rootDir.absolutePath)

    // Locale-sensitive formatting inside the harness itself, and the encoding
    // the ledger is written in. Neither should depend on the machine.
    systemProperty("file.encoding", "UTF-8")
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
}

/**
 * Rewrites the divergence ledger from what ICU says today.
 *
 * The normal flow is `:conformance-icu:test`, which reads the ledger and fails
 * on anything it does not already record. This task is for the two cases where
 * the ledger is supposed to move: an ICU or CLDR version bump, and a deliberate
 * change to what this library answers. Both are meant to produce a reviewable
 * diff rather than a silent pass.
 */
val updateLedger by tasks.registering(Test::class) {
    group = "verification"
    description = "Regenerates conformance/ledger from the current ICU4J and the current tables."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    maxHeapSize = "3g"
    systemProperty("kotlinx.locale.rootDir", rootDir.absolutePath)
    systemProperty("kotlinx.locale.ledger.write", "true")
    // Only the ICU comparisons. Without this the digest generator runs too and
    // fails, because it is not in write mode and its fixtures may not exist yet.
    environment("TESTBALLOON_INCLUDE_PATTERNS", "dev.carcara.kotlinx.locale.icu.NameConformanceTest*")
    // Always runs: its whole purpose is to observe the world as it is now.
    outputs.upToDateWhen { false }
}

/**
 * Rewrites the per-locale digests the other targets are held to.
 *
 * Separate from [updateLedger] because they answer different questions and move
 * for different reasons: the ledger moves when ICU does, the digests move when
 * this library's own output does.
 */
val updateDigests by tasks.registering(Test::class) {
    group = "verification"
    description = "Regenerates the cross-target digest fixtures from this JVM's answers."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    maxHeapSize = "3g"
    systemProperty("kotlinx.locale.rootDir", rootDir.absolutePath)
    systemProperty("kotlinx.locale.digests.write", "true")
    environment("TESTBALLOON_INCLUDE_PATTERNS", "dev.carcara.kotlinx.locale.icu.DigestGeneratorTest*")
    outputs.upToDateWhen { false }
}
