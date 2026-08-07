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
    // Measured at 982.8 KB with the country, currency, datetime and locale
    // catalog artifacts. Deliberately not every artifact in the library: the
    // language names alone are larger than all of these together, and the
    // exemplar cities are larger again, so folding them in would make this row
    // a number nobody's build resembles.
    budgetBytes = 1100 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-country-cldr-full"))
        implementation(project(":kotlinx-locale-currency-cldr-full"))
        implementation(project(":kotlinx-locale-datetime-cldr-full"))
        implementation(project(":kotlinx-locale-types"))
    }
}
