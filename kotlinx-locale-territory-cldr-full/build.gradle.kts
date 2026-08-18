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

// The territory names, which the country domain and the language domain both
// need and which used to ship in each of them.
//
// A module of its own rather than a dependency from one domain on the other, so
// that neither pays for the other's types: the table is keyed by alpha-2 code
// rather than by `Country`, so a language picker asking for region names does
// not compile the 249-entry enum to get them.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
