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

// "2 US dollars": the currency names that agree with a count, in their own
// artifact.
//
// 42,712 count-keyed names across the 213 locales that declare them, which is
// several times every other currency table put together. Symbols, display names,
// patterns and parsing all work without them, so this is worth asking for
// deliberately.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The count-less display name is the third step of the fallback
            // chain, and it lives in the currency binding's own table.
            api(project(":kotlinx-locale-currency-cldr-full"))
            // The plural rules that choose which of the forms a number takes.
            api(project(":kotlinx-locale-number-cldr-full"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":conformance-test-suite"))
        }
    }
}
