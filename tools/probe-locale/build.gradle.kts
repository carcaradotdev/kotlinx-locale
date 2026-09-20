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
    // About half this reading is the `-u-` extension machinery: the attribute
    // list, the keyword map and the sorting that writes them back in Annex C
    // order reach for standard library collections Kotlin/JS eliminates from a
    // `Locale` without them. 18 KB left 0.4 KB of headroom, which is not enough
    // room for an unrelated edit to Locale.kt.
    budgetBytes = 20 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-core"))
    }
}
