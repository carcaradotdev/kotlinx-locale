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

// The phone number value type, the source contracts and the option enums.
//
// The data behind them is Google's libphonenumber rather than CLDR, which is
// why the layer below is named for it: the numbering plans are ITU-T E.164 and
// libphonenumber is the machine-readable form of them the industry maintains.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-core"))
            api(project(":kotlinx-locale-country-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
