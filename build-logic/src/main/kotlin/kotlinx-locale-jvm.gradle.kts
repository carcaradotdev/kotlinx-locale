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
    id("kotlinx-locale-ktlint")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
