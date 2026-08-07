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
 * This is the vanniktech base plugin rather than the main one: the main plugin
 * fills the POM from `POM_*` Gradle properties, which is the documented
 * Isolated Projects incompatibility, and the POM below is the same for every
 * module anyway.
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

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    configureBasedOnAppliedPlugins()

    pom {
        name.set(project.name)
        // A provider because the module sets its description after this plugin
        // is applied; read eagerly it would always be the fallback.
        description.set(
            provider {
                project.description
                    ?: "Locale support for Kotlin Multiplatform, written entirely in common Kotlin"
            },
        )
        inceptionYear.set("2026")
        url.set("https://github.com/carcaradotdev/kotlinx-locale")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("DevSrSouza")
                name.set("Gabriel Souza")
                url.set("https://github.com/DevSrSouza")
            }
        }
        scm {
            url.set("https://github.com/carcaradotdev/kotlinx-locale")
            connection.set("scm:git:git://github.com/carcaradotdev/kotlinx-locale.git")
            developerConnection.set("scm:git:ssh://git@github.com/carcaradotdev/kotlinx-locale.git")
        }
    }
}
