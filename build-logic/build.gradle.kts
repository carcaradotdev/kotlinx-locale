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

import org.gradle.plugin.devel.tasks.ValidatePlugins

plugins {
    `kotlin-dsl`
}

group = "dev.carcara.build-logic"

dependencies {
    implementation(libs.gradle.plugin.kotlin.multiplatform)
    implementation(libs.gradle.plugin.android.kmp.library)
    implementation(libs.gradle.plugin.ktlint)
    implementation(libs.gradle.plugin.kotlin.serialization)
    implementation(libs.gradle.plugin.maven.publish)
    implementation(libs.gradle.plugin.testballoon)
    implementation(libs.gradle.plugin.kover)
}

// Strict validation turns a missing annotation or an implicit ABSOLUTE path
// sensitivity into a build failure instead of a warning nobody reads, which is
// what mechanically enforces the input declarations the build cache keys on.
tasks.withType<ValidatePlugins>().configureEach {
    failOnWarning = true
    enableStricterValidation = true
}
