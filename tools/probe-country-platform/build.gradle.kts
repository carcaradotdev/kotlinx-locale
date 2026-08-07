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
    // The same calls as probe-country-full, answered by the host instead of by
    // bundled tables. The budget is deliberately close to probe-country-codes:
    // if this ever approaches probe-country-full, something pulled CLDR data
    // back into the platform path.
    budgetBytes = 25 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-country-platform"))
    }
}
