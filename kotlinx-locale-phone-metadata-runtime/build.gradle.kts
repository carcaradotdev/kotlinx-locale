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

// The code that operates on libphonenumber-shaped data: the bounded pattern
// matcher, the parser, the validator and the formatters. No metadata; that
// comes from -metadata-full or from a plugin-generated source set.
//
// The matcher is why this domain is pure common Kotlin. libphonenumber
// validates with regular expressions, and Kotlin's `Regex` delegates to a
// different engine on every target, so using it would mean a number that
// validates on Android and not on JS. The patterns turn out to need only
// alternation, character classes, `\d`, groups, bounded repetition and an end
// anchor, so this module evaluates that subset itself and generation fails if
// a later libphonenumber release steps outside it.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-phone-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
