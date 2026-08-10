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

/**
 * Maven Central publication for every published module, whatever its shape:
 * `configureBasedOnAppliedPlugins` picks the right publication setup for a
 * multiplatform module, a plain JVM one, or the Gradle plugin, so this is the
 * single place that knows the modules publish at all. The plugins that decide
 * the shape must already be applied when this one is, which the plugins blocks
 * of the applying modules guarantee.
 *
 * This is the vanniktech base plugin rather than the main one. The main plugin
 * fills the POM from `POM_*` Gradle properties by reading them off the project,
 * which is the documented Isolated Projects incompatibility. The properties
 * below carry the same names, because that is what anyone who has published a
 * library before will look for, but they are read through `providers`, which is
 * the form Isolated Projects allows.
 *
 * They live in the root `gradle.properties` and are the same for every module.
 * A module contributes only its own name and description.
 *
 * Publishing needs credentials the build does not carry: a Central Portal
 * token as `mavenCentralUsername`/`mavenCentralPassword` and a GPG key as
 * `signingInMemoryKey`/`signingInMemoryKeyPassword`, both usually injected as
 * `ORG_GRADLE_PROJECT_*` environment variables in CI. Signing is only required
 * for non-SNAPSHOT versions, so `publishToMavenLocal` works without a key.
 */
plugins {
    id("com.vanniktech.maven.publish.base")
}

/**
 * One `POM_*` property, refused when it is absent or blank.
 *
 * Blank counts as absent because a property left with nothing after the `=` is
 * the shape a half-filled gradle.properties takes, and an empty `<url>` reaches
 * the Portal as an invalid POM rather than as a missing one.
 *
 * Read at configuration rather than wired as a provider, so a missing property
 * names itself here instead of surfacing as "no value present" while a release
 * is generating POMs. Going through `providers` is what makes the read a build
 * input, so editing gradle.properties invalidates the configuration cache.
 */
fun pomProperty(name: String): String =
    providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: error(
            "$name is missing or blank in the root gradle.properties. Every published POM carries " +
                "it, and Maven Central rejects a deployment whose POM is incomplete.",
        )

val fallbackDescription = pomProperty("POM_DESCRIPTION")

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    configureBasedOnAppliedPlugins()

    pom {
        name.set(project.name)
        // A provider because the module sets its description after this plugin
        // is applied; read eagerly it would always be the fallback.
        description.set(provider { project.description ?: fallbackDescription })
        inceptionYear.set(pomProperty("POM_INCEPTION_YEAR"))
        url.set(pomProperty("POM_URL"))
        licenses {
            license {
                name.set(pomProperty("POM_LICENSE_NAME"))
                url.set(pomProperty("POM_LICENSE_URL"))
                distribution.set(pomProperty("POM_LICENSE_DIST"))
            }
        }
        developers {
            developer {
                id.set(pomProperty("POM_DEVELOPER_ID"))
                name.set(pomProperty("POM_DEVELOPER_NAME"))
                url.set(pomProperty("POM_DEVELOPER_URL"))
            }
        }
        scm {
            url.set(pomProperty("POM_SCM_URL"))
            connection.set(pomProperty("POM_SCM_CONNECTION"))
            developerConnection.set(pomProperty("POM_SCM_DEV_CONNECTION"))
        }
    }
}
