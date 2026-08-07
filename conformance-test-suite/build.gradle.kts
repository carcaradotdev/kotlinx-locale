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

// The ICU fixtures and the assertions over them, for this build's own test
// source sets.
//
// Not published, which is why it applies the base convention plugin rather than
// `kotlinx-locale-multiplatform`: no publication, and no committed ABI dump for
// an ABI nobody outside this build compiles against. The six modules that use it
// take it as a project dependency in commonTest.
plugins {
    id("kotlinx-locale-multiplatform-base")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-country-core"))
            api(project(":kotlinx-locale-currency-core"))
            api(project(":kotlinx-locale-datetime-core"))
            // The skeleton contract lives in -cldr-runtime rather than -core,
            // because no platform source can answer it. The fixtures for it
            // therefore need the runtime module, not just the core one.
            api(project(":kotlinx-locale-datetime-cldr-runtime"))
            api(project(":kotlinx-locale-number-core"))
            api(project(":kotlinx-locale-timezone-core"))
            // api on purpose: the suite reports through assertions, so a caller
            // is already writing them. Not kotlin-test, which cannot share a
            // Kotlin/Wasm compilation with the test framework.
            api(project(":test-assertions"))
        }
        // kotlin-test used to be declared again here, for the Android host test
        // compilation, which does not inherit it through the `api` above. The
        // multiplatform convention plugin now puts it on every commonTest, so
        // repeating it landed it on this module's test compilation twice and
        // Kotlin/Wasm emitted two `startUnitTests` entry points into one module.
        // Nothing is lost by dropping it: the convention plugin covers the case
        // the original comment was about.
    }
}
