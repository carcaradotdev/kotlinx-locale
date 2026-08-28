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

// The generated root table and every locale tailoring CLDR has, plus the binding
// object and its entry points.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-collation-cldr-runtime"))
        }
        // No test dependency block. The assertions and the framework come from
        // the convention plugin, and `:conformance-test-suite` is not taken:
        // it compiles into every test binary that depends on it, and this
        // domain's fixtures are ICU comparisons in `:conformance-icu` rather
        // than a generated case file.
    }
}
