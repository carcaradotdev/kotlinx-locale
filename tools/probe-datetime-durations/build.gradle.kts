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

plugins {
    id("kotlinx-locale-size-probe")
}

sizeProbe {
    // Measured at 117.3 KB. Most of it is the wording, and the number and plural
    // tables it renders counts through are the rest. Smaller than the relative
    // probe next door because fourteen units of seven slots is less than eight
    // units of eighteen.
    budgetBytes = 150 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-datetime-cldr-durations"))
    }
}
