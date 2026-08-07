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

// The assertion vocabulary the test suites are written in.
//
// This exists because kotlin-test cannot share a Kotlin/Wasm compilation with
// TestBalloon: both emit a `startUnitTests` entry point, a Wasm module cannot
// export one name twice, and Node refuses to load the result. Kotlin/JS is
// unaffected, so the collision only shows up on the two Wasm targets.
//
// The alternative was to write every assertion in Kotest's infix style, which
// the matrix addon already brings. That is a different reading order at ten
// thousand call sites and flips the argument order of the most common one, so
// the risk is not in the idea but in the transcription. These functions carry
// kotlin-test's names, parameter order and defaults instead, and the migration
// was an import swap.
//
// No dependencies on purpose: this lands on every test compilation in the build,
// including modules whose own dependency graph it must not disturb.
plugins {
    id("kotlinx-locale-multiplatform-base")
}
