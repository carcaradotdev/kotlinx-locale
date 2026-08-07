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

// The skeleton tables: what a locale's availableFormats, appendItems and quarter
// names resolve to, for every locale CLDR has. Opt in, and its own artifact,
// because the tables are around 210 KB of raw payload against the 435 KB the
// whole of -cldr-full weighs — folding them in would make every consumer of
// ordinary date formatting pay for skeletons.
//
// The matcher is in -cldr-runtime rather than here, for the same reason the
// pattern formatter is: a build narrowed to three locales through the Gradle
// plugin generates its own tables and still needs the algorithm.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api, not implementation: the binding object exposes SkeletonFormatSource,
            // and reads its patterns through CldrDateTime.
            api(project(":kotlinx-locale-datetime-cldr-full"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":conformance-test-suite"))
        }
    }
}
