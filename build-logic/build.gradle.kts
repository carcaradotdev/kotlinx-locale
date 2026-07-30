import org.gradle.plugin.devel.tasks.ValidatePlugins

plugins {
    `kotlin-dsl`
}

group = "dev.carcara.build-logic"

dependencies {
    implementation(libs.gradle.plugin.kotlin.multiplatform)
    implementation(libs.gradle.plugin.android.kmp.library)
    implementation(libs.gradle.plugin.ktlint)
    implementation(libs.gradle.plugin.maven.publish)
}

// Strict validation turns a missing annotation or an implicit ABSOLUTE path
// sensitivity into a build failure instead of a warning nobody reads, which is
// what mechanically enforces the input declarations the build cache keys on.
tasks.withType<ValidatePlugins>().configureEach {
    failOnWarning = true
    enableStricterValidation = true
}
