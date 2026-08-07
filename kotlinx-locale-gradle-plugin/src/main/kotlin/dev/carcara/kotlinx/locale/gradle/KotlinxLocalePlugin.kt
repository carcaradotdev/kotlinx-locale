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

package dev.carcara.kotlinx.locale.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.Properties

/**
 * Generates a locale data set narrowed to the locales this build declares.
 *
 * The saving is worth the ceremony: the full CLDR set is roughly 820 KB gzipped
 * on Kotlin/JS with every domain in use, and trimming it to a handful of locales
 * takes it to a small fraction of that. On JVM, Android and Native, where
 * nothing is eliminated, this is the only lever there is.
 *
 * ```
 * plugins {
 *     id("dev.carcara.kotlinx-locale")
 * }
 *
 * kotlinxLocale {
 *     locales(PT.BR, EN.US)
 *     fallback(EN.US)
 *     packageName = "com.example.locale"
 *     country { names = true }
 * }
 * ```
 *
 * The generated sources implement the same interfaces the shipped artifacts do
 * and carry the same convenience extensions, so moving between a narrowed build
 * and a full one is a dependency change and an import.
 */
class KotlinxLocalePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create("kotlinxLocale", KotlinxLocaleExtension::class.java)
        extension.packageName.convention("kotlinx.locale.generated")
        extension.objectPrefix.convention("Generated")
        // Feature flags default themselves as they are declared; see FeatureBlock.flag.

        val cldrData = declareCldrDataConfiguration(target)
        val generate = registerGenerateTask(target, extension, cldrData)
        wireIntoSourceSets(target, generate)
    }

    /**
     * The bundle the emitters read, resolved from the consumer's repositories.
     *
     * A default dependency rather than a hard-coded one, so a consumer can move
     * to a different CLDR release by declaring `kotlinxLocaleCldrData` themselves
     * without waiting for a plugin release.
     */
    private fun declareCldrDataConfiguration(target: Project): Provider<out Iterable<java.io.File>> {
        val declared = target.configurations.dependencyScope("kotlinxLocaleCldrData") { configuration ->
            configuration.description = "The pre-resolved CLDR bundle the locale generator reads"
            // A default rather than an unconditional dependency: declaring
            // kotlinxLocaleCldrData replaces it, which is how a build pins a
            // different CLDR release or points at a bundle of its own.
            configuration.defaultDependencies { dependencies ->
                dependencies.add(target.dependencies.create("dev.carcara:kotlinx-locale-codegen-data:$PLUGIN_VERSION"))
            }
        }
        val resolvable = target.configurations.resolvable("kotlinxLocaleCldrDataClasspath") { configuration ->
            configuration.description = "Resolves the CLDR bundle for the locale generator"
            configuration.extendsFrom(declared.get())
        }
        return resolvable.map { it.incoming.files }
    }

    private fun registerGenerateTask(
        target: Project,
        extension: KotlinxLocaleExtension,
        cldrData: Provider<out Iterable<java.io.File>>,
    ): TaskProvider<GenerateLocaleSources> = target.tasks.register(
        "generateLocaleSources",
        GenerateLocaleSources::class.java,
    ) { task ->
        task.group = "kotlinx-locale"
        task.description = "Generates locale data for the locales this build declares"
        task.cldrData.from(cldrData)
        task.locales.set(extension.locales)
        task.fallbackLocale.set(extension.fallbackLocale)
        task.packageName.set(extension.packageName)
        task.objectPrefix.set(extension.objectPrefix)
        task.features.set(
            target.provider {
                require(!extension.generatesNothing()) {
                    "kotlinxLocale generates nothing: enable at least one of " +
                        LocaleFeature.entries.joinToString { it.dslName }
                }
                extension.requestedFeatures()
            },
        )
        task.outputDirectory.set(target.layout.buildDirectory.dir("generated/kotlinx-locale"))
    }

    /**
     * Adds the generated directory to the Kotlin source set that would use it.
     *
     * `withId` rather than a check after the fact, so the order the plugins are
     * applied in does not matter. Passing the task provider to `srcDir` is what
     * carries the dependency: the compile task depends on generation because the
     * data flows, not because anything said so.
     */
    private fun wireIntoSourceSets(target: Project, generate: TaskProvider<GenerateLocaleSources>) {
        val generated = generate.map { it.outputDirectory }

        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            target.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlin ->
                kotlin.sourceSets.named("commonMain") { it.kotlin.srcDir(generated) }
            }
        }
        target.plugins.withId("org.jetbrains.kotlin.jvm") {
            target.extensions.configure(KotlinJvmProjectExtension::class.java) { kotlin ->
                kotlin.sourceSets.named("main") { it.kotlin.srcDir(generated) }
            }
        }
    }

    internal companion object {
        /**
         * The plugin's own version, which is also the version of the bundle it
         * defaults to.
         *
         * Read from a resource written at build time rather than hard-coded,
         * because a version that has to be edited by hand is a version that will
         * be wrong after a release.
         */
        val PLUGIN_VERSION: String by lazy {
            val resource = KotlinxLocalePlugin::class.java.getResourceAsStream("/kotlinx-locale-plugin.properties")
                ?: error("kotlinx-locale-plugin.properties is missing from the plugin jar")
            resource.use { stream -> Properties().apply { load(stream) }.getProperty("version") }
                ?: error("kotlinx-locale-plugin.properties carries no version")
        }
    }
}
