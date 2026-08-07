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

// The published Gradle plugin. It runs the emitters from
// :kotlinx-locale-codegen-emitters against the bundle the consumer resolves from
// :kotlinx-locale-codegen-data.
plugins {
    `java-gradle-plugin`
    id("kotlinx-locale-jvm")
    id("kotlinx-locale-publish")
}

dependencies {
    // The emitters are pinned to the plugin: a plugin and an emitter that
    // disagree about the generator API is not a combination anyone should be
    // able to assemble. The data is not pinned, so a consumer can move to a
    // newer CLDR release without waiting for a plugin release.
    implementation(project(":kotlinx-locale-codegen-emitters"))

    // Only to wire the generated directory into the right Kotlin source set. The
    // consumer's own Kotlin plugin provides it at runtime, so compileOnly.
    compileOnly(libs.gradle.plugin.kotlin.multiplatform)

    // LocaleRef so the DSL can take a locale rather than a string, and the
    // catalog so a consumer can write PT.BR in their own build script.
    api(project(":kotlinx-locale-core"))
    api(project(":kotlinx-locale-types"))

    testImplementation(libs.kotlin.test)
    testImplementation(gradleTestKit())

    // What GeneratedSourceCompilesTest compiles each feature's output against.
    // A feature declares the closure of tables it needs, and until something
    // compiled the result that closure was only ever checked against file names:
    // three timezone flags and one currency flag shipped emitting a source file
    // that referred to a registry no table wrote. These are the JVM variants of
    // the multiplatform runtime modules, which is what a narrowed consumer
    // depends on in place of -cldr-full.
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(project(":kotlinx-locale-country-cldr-runtime"))
    testImplementation(project(":kotlinx-locale-currency-cldr-runtime"))
    testImplementation(project(":kotlinx-locale-datetime-cldr-runtime"))
    testImplementation(project(":kotlinx-locale-language-cldr-runtime"))
    testImplementation(project(":kotlinx-locale-number-cldr-runtime"))
    testImplementation(project(":kotlinx-locale-personname-cldr-runtime"))
    testImplementation(project(":kotlinx-locale-timezone-cldr-runtime"))
}

gradlePlugin {
    plugins {
        create("kotlinxLocale") {
            id = "dev.carcara.kotlinx-locale"
            implementationClass = "dev.carcara.kotlinx.locale.gradle.KotlinxLocalePlugin"
            displayName = "kotlinx-locale"
            description = "Generates a locale data set narrowed to the locales a build declares"
        }
    }
}

// Strict validation so a missing annotation or an implicit ABSOLUTE path
// sensitivity on the generation task fails rather than warns.
tasks.withType<org.gradle.plugin.devel.tasks.ValidatePlugins>().configureEach {
    failOnWarning = true
    enableStricterValidation = true
}

// The plugin needs its own version to default the bundle dependency to a
// matching release. Writing it as a resource beats hard-coding a string that
// would be wrong the first time nobody remembers to edit it.
val pluginVersion by tasks.registering(WriteProperties::class) {
    destinationFile = layout.buildDirectory.file("generated/version/kotlinx-locale-plugin.properties")
    property("version", project.version.toString())
}

sourceSets.main {
    output.dir(pluginVersion.map { it.destinationFile.get().asFile.parentFile })
}

tasks.test {
    // The functional tests need the bundle to narrow, and resolving it from
    // Maven inside a nested build would mean publishing first. Handing them the
    // checked-in resource directly keeps them fast and hermetic.
    systemProperty(
        "kotlinx.locale.bundle",
        rootProject.layout.projectDirectory
            .file("kotlinx-locale-codegen-data/src/main/resources/dev/carcara/kotlinx/locale/cldr-data.txt")
            .asFile.absolutePath,
    )
}
