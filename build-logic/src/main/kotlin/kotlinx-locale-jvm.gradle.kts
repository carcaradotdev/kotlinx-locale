/**
 * A plain JVM module: the code generator, the emitters it publishes, and the
 * CLDR bundle.
 *
 * These are not multiplatform and are never consumed from common code, so they
 * skip the target matrix, the ABI dumps and the Android setup that
 * `kotlinx-locale-multiplatform` brings.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("de.infix.testBalloon")
    id("kotlinx-locale-ktlint")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(21)
}

dependencies {
    // See kotlinx-locale-multiplatform-base: the assertions are this build's
    // own, so that exactly one test framework reaches any compilation.
    "testImplementation"(project(":test-assertions"))
    "testImplementation"(libs.findLibrary("testballoon-framework-core").get())
    "testImplementation"(libs.findLibrary("testballoon-matrix").get())
    "testImplementation"(libs.findLibrary("kotest-assertions-core").get())
}

// Redundant with what the TestBalloon plugin already sets on every non-Android
// Test task, and kept anyway. The failure it guards against is a `test` task
// falling back to JUnit 4 while kotlin-test resolves to its JUnit 5 variant:
// that does not fail, it reports zero tests and passes green. One line is
// cheaper than a class of bug nobody reads a log for.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // See the note in kotlinx-locale-multiplatform-base: an unset ceiling is a
    // quarter of physical memory, which is a different number on every machine.
    // :conformance-icu raises this, because ICU caches a resource bundle per
    // locale and never evicts; nothing else here needs to.
    maxHeapSize = "1g"
}
