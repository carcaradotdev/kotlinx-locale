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

// The Unicode Collation Algorithm over a CLDR-shaped weight table: collation
// elements, contractions, prefixes and sort keys. Carries no table; it is a
// constructor argument, so a build narrowed through the Gradle plugin uses the
// same code as the full one.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-collation-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
