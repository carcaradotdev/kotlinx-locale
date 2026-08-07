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

// A standalone build, deliberately not part of the root build: it drags in the
// webpack toolchain and would otherwise pollute the root `kotlin-js-store/yarn.lock`
// and slow down `./gradlew build` in CI. It consumes the library modules through
// an included build, so it always measures the working tree, never a published
// artifact.
pluginManagement {
    val catalog = java.io.File(settingsDir, "../../gradle/libs.versions.toml").readText()
    val kotlinVersion = Regex("""^kotlin\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
        .find(catalog)!!
        .groupValues[1]

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("org.jetbrains.kotlin.multiplatform") version kotlinVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "js-size"

includeBuild("../..")
