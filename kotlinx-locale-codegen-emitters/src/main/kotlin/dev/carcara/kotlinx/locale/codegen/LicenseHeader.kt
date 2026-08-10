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

package dev.carcara.kotlinx.locale.codegen

import java.io.File

/**
 * The Apache license notice every emitted file opens with, followed by the blank
 * line that separates it from the `// GENERATED` marker.
 *
 * A generated source is a shipped source, so it carries the notice like any
 * other. It is also rewritten whole on every run, which is why the notice cannot
 * be something a script adds afterwards: it has to come out of the emitter, or
 * the next `./gradlew :codegen:generateLocaleData` would strip a thousand of
 * them and `scripts/license_header.py check` would fail on the result.
 *
 * `LicenseHeaderTest` pins this to the LICENSE file at the repository root,
 * which is where that script reads the same text from. The two are not allowed
 * to drift.
 */
public val LICENSE_HEADER: String = """
    |/*
    | * Copyright 2026 Carcara.dev
    | *
    | * Licensed under the Apache License, Version 2.0 (the "License");
    | * you may not use this file except in compliance with the License.
    | * You may obtain a copy of the License at
    | *
    | *     http://www.apache.org/licenses/LICENSE-2.0
    | *
    | * Unless required by applicable law or agreed to in writing, software
    | * distributed under the License is distributed on an "AS IS" BASIS,
    | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    | * See the License for the specific language governing permissions and
    | * limitations under the License.
    | */
""".trimMargin() + "\n\n"

/**
 * The `package` line a generated file opens with, read off where it is written.
 *
 * Here for the same reason as [LICENSE_HEADER], and found the same way. Ten
 * fixture emitters wrote one hardcoded package while `Main.kt` decided their
 * directories separately, so moving a fixture into the module that reads it
 * moved the file and left the declaration behind. The tree looked right because
 * the move rewrote the committed files; the next
 * `./gradlew :codegen:generateLocaleData` put all ten back and stopped the test
 * sources compiling.
 *
 * Deriving it from the path means the two cannot disagree: a fixture that moves
 * takes its package with it.
 */
public fun packageOf(outputFile: File): String {
    val parts = outputFile.absoluteFile.parentFile.invariantSeparatorsPath.split('/')
    val kotlin = parts.lastIndexOf("kotlin")
    require(kotlin >= 0 && kotlin + 1 < parts.size) {
        "cannot tell what package $outputFile belongs to: no src/<sourceSet>/kotlin/<package> on its path"
    }
    return parts.drop(kotlin + 1).joinToString(".")
}
