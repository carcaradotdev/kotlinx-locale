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

// Everything the root project does lives in the convention plugin, so this file
// stays a declaration of what the root is rather than a place logic accumulates.
// Shared configuration reaches the modules through the convention plugins they
// apply, never through allprojects or subprojects.
plugins {
    id("kotlinx-locale-verification")
    // Coverage aggregation is a property of the whole build rather than of any
    // module, and Kover merges through dependencies declared at the root. This
    // is the one thing the root owns beyond verification.
    id("kotlinx-locale-coverage")
}

/**
 * What the coverage number is about: the published library.
 *
 * `gradle/coverage-exempt.txt` names the modules that are not it, and says why.
 * The same list switches Kover off inside those modules, so a module cannot be
 * absent from the headline number and still write a report of its own.
 */
val notTheLibrary = providers
    .fileContents(layout.projectDirectory.file("gradle/coverage-exempt.txt"))
    .asText
    .map { text ->
        text.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }
    .get()

dependencies {
    for (module in rootProject.subprojects) {
        // `:tools` itself is the container Gradle creates for `:tools:probe-*`.
        // It has no build file and so no Kover variant to aggregate.
        if (module.path == ":tools" || module.path.startsWith(":tools:")) continue
        if (module.path in notTheLibrary) continue
        kover(project(module.path))
    }
}
