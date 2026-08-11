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

// The record format and the codecs that rewrite it, with nothing above them.
// This module holds no File, no Kotlin syntax and no CLDR vocabulary: it maps
// payload strings to payload strings, so a codec can be written and measured
// without a code generator anywhere near it.
plugins {
    id("kotlinx-locale-jvm")
    id("kotlinx-locale-publish")
}

kotlin {
    explicitApi()
}

dependencies {
    testImplementation(libs.kotlin.test)
}
