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
    // Measured at 1291.1 KB, the largest artifact in the library by a wide
    // margin: every language, script and region name in every locale. This is
    // the strongest argument for the Gradle plugin in the whole project, since
    // a language picker needs a handful of names rather than eleven hundred
    // locales' worth.
    budgetBytes = 1400 * 1024
}

kotlin {
    sourceSets.jsMain.dependencies {
        implementation(project(":kotlinx-locale-language-cldr-full"))
    }
}
