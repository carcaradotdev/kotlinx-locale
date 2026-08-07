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

// "2 hours", "2 hr", "2h" and their wording in every locale.
//
// Its own artifact rather than part of -cldr-full, for the reason -cldr-relative
// is: duration wording needs no date patterns and no month names, so a consumer
// who only wants this should not carry them.
//
// Not the same thing as durationPattern in -cldr-full, which gives h:mm. That is
// a clock reading and three patterns wide; this is the measurement form, with a
// plural rule behind every one of its fourteen units.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-datetime-cldr-runtime"))
            // The plural rules that pick the wording and the formatter that
            // renders its count.
            api(project(":kotlinx-locale-number-cldr-full"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
