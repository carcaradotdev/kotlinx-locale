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

// The code that turns CLDR-shaped zone data into a name: the localized GMT
// format, metazone resolution and the fallback ladder of UTS #35 Part 4. No
// records — those come from -cldr-full and -cldr-cities.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-timezone-core"))
            // The locale's own digits for the GMT offset.
            api(project(":kotlinx-locale-number-cldr-runtime"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
