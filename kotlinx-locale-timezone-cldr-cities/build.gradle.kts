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

// The exemplar cities, in their own artifact.
//
// 38,323 names across the locales that declare them, which is the largest table
// in the library after the language names. Everything except the generic
// location format works without them, and that format's own fallback is the one
// UTS #35 prescribes, so this is worth asking for deliberately.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-timezone-cldr-full"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":conformance-test-suite"))
            implementation(project(":kotlinx-locale-country-cldr-full"))
            implementation(project(":kotlinx-locale-number-cldr-full"))
        }
    }
}
