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

// The code that operates on CLDR-shaped number data: the symbol record reader,
// the pattern engine, the compact-notation algorithm, the plural-rule evaluator
// and the ordinal rules. No records — those come from -cldr-full or from a
// plugin-generated source set.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-number-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
