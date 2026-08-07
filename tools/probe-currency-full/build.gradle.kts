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
    // Measured at 456.1 KB, up from 370 KB. Money now formats through the
    // shared number engine, so this pulls in the number symbol and pattern
    // tables, the plural rules that compact money is keyed by, and the compact
    // money table itself. A consumer who wants only names and standard
    // formatting pays for the plural rules, which are four kilobytes.
    budgetBytes = 500 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-currency-cldr-full"))
    }
}
