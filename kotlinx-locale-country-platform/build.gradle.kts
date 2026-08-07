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

// Country names from the host platform rather than from bundled CLDR data.
//
// Note what is absent: no dependency on -cldr-full or -cldr-runtime. That is
// the point of the layering. A build that takes this module ships no name
// tables at all.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-country-core"))
            api(project(":kotlinx-locale-platform"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":conformance-test-suite"))
            // To compose with, and to compare against, on the targets that have
            // no platform data of their own.
            implementation(project(":kotlinx-locale-country-cldr-full"))
        }
    }
}
