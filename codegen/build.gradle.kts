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

// Extraction: clones the pinned CLDR and ICU repositories and parses their XML.
// This half cannot run in a user's build, which is why the emitters live in
// :kotlinx-locale-codegen-emitters and the resolved data in :kotlinx-locale-codegen-data.
plugins {
    id("kotlinx-locale-jvm")
}

dependencies {
    implementation(project(":kotlinx-locale-codegen-emitters"))
    // Generates the skeleton goldens and nothing else. This module runs at build
    // time and is never published, so ICU cannot reach a consumer's classpath.
    implementation(libs.icu4j)
    implementation(libs.libphonenumber)
    testImplementation(libs.kotlin.test)
}

val mainClassFqn = "dev.carcara.kotlinx.locale.codegen.MainKt"

// Clones the pinned CLDR and ICU repositories into codegen/repos/ (gitignored).
tasks.register<JavaExec>("cloneLocaleRepos") {
    group = "codegen"
    description = "Clone the pinned CLDR and ICU repositories into codegen/repos/"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassFqn
    args("clone", rootDir.absolutePath)
}

// Full pipeline: clone (if needed) + parse + write the bundle + generate every
// shipped Kotlin source from it.
tasks.register<JavaExec>("generateLocaleData") {
    group = "codegen"
    description = "Generate the CLDR bundle and every generated Kotlin source from it"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassFqn
    args("generate", rootDir.absolutePath)
}

tasks.test {
    // The round-trip test regenerates the shipped sources and diffs them, so it
    // needs to know where the checked-in ones are.
    systemProperty("kotlinx.locale.rootDir", rootDir.absolutePath)
}
