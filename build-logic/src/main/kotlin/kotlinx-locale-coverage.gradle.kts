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

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

/**
 * Line and branch coverage over the code a person wrote.
 *
 * ## Why the filters are the whole design
 *
 * 1003 of this build's 1160 main source files are generated: locale tables,
 * country and currency enums, the typed locale catalog. Measured raw, coverage
 * reports on whether a test happened to touch a particular string constant in
 * `LocaleData_q.kt`, and the headline number moves when CLDR adds a locale.
 * That is worse than no number, because it looks like one.
 *
 * What is worth measuring is the ~157 hand-written files: the parsers, the
 * matchers, the renderers, the fallback ladders. Those are where a bug can hide,
 * and they are what the conformance suites exercise indirectly. Coverage here
 * answers a question the conformance suites cannot: which branch has nothing
 * pointing at it at all.
 *
 * ## Why it only measures the JVM
 *
 * Kover instruments JVM bytecode. The sources are common, so a line covered on
 * the JVM is the same line on every other target; what the JVM number cannot see
 * is an `actual` declaration in a native or JS source set. Those are few and are
 * listed as a known limit rather than papered over.
 *
 * ## Why some modules are switched off entirely
 *
 * This plugin arrives through `kotlinx-locale-multiplatform-base`, which every
 * multiplatform module applies, including the two that exist only to test the
 * others. Left alone they write a report each, and those reports are read by
 * `.github/scripts/kover_summary.py` straight off disk, so the per-module table
 * carried a `test-assertions 0.0%` row that measured nothing. `gradle/coverage-
 * exempt.txt` says which modules are not the library, and a listed one gets
 * [KoverProjectExtension.disable] rather than a filter: no instrumentation, no
 * report, nothing on disk to read by accident.
 */
plugins {
    id("org.jetbrains.kotlinx.kover")
}

val coverageExempt = providers
    .fileContents(rootProject.layout.projectDirectory.file("gradle/coverage-exempt.txt"))
    .asText
    .map { text ->
        text.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }
    .getOrElse(emptySet())

extensions.configure<KoverProjectExtension> {
    if (project.path in coverageExempt) {
        disable()
        return@configure
    }

    reports {
        filters {
            excludes {
                // Generated tables. Named by package rather than by file, because
                // the emitters decide file names and a glob over those would rot
                // the first time one is renamed.
                packages(
                    "dev.carcara.kotlinx.locale.*.internal.data",
                    "dev.carcara.kotlinx.locale.internal.data",
                    // The typed locale catalog: 322 generated enums, one per CLDR
                    // language, whose every member is a constant.
                    "dev.carcara.kotlinx.locale.catalog",
                    // Conformance fixtures, which are test data rather than code.
                    "dev.carcara.kotlinx.locale.*.conformance",
                    "dev.carcara.kotlinx.locale.conformance",
                )
                // The generated source objects: `CldrCountry`, `CldrNumber` and
                // the rest are emitter output that binds a table to an interface.
                // The interface is hand-written and measured; the binding is not.
                classes(
                    "dev.carcara.kotlinx.locale.*.cldr.Cldr*",
                    "dev.carcara.kotlinx.locale.*.cldr.*.Cldr*",
                )
                // Enum plumbing the compiler writes.
                annotatedBy("kotlin.jvm.JvmSynthetic")
            }
        }

        total {
            xml {
                // Consumed by the coverage workflow; the HTML is for a person.
                onCheck = false
            }
            html {
                onCheck = false
            }
        }
    }
}
