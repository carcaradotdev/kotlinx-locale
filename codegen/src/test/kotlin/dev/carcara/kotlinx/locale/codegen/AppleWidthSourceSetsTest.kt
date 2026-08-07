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

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue
import java.io.File

/**
 * Holds the two Apple source sets that exist only because of a pointer width to
 * being copies of each other.
 *
 * `NSInteger` and `NSUInteger` are 32 bits wide on watchosArm32 and watchosArm64
 * and 64 bits everywhere else Apple, and Kotlin refuses a type of varying width
 * in a source set spanning both. The Foundation calls that name one therefore
 * live in `appleIlp32Main` and `appleLp64Main` rather than in `appleMain`, and
 * the two copies are identical by construction: nothing about the logic differs,
 * only the width the compiler resolves the types to.
 *
 * That makes them a drift hazard of the worst kind, because an edit to one
 * compiles perfectly well and changes behaviour on some Apple targets and not
 * others. Nothing else in the build would notice. This does.
 *
 * It lives in `:codegen` because that is the only JVM module with a test source
 * set and a handle on the repository root; it has nothing to do with generating
 * data.
 */
val AppleWidthSourceSetsTest by matrixSuite {

    val rootDir = File(
        System.getProperty("kotlinx.locale.rootDir") ?: error("kotlinx.locale.rootDir is not set"),
    )

    test("theWidthSpecificSourceSetsAreCopiesOfEachOther") {
        val modules = rootDir.listFiles { file: File -> file.isDirectory && file.name.startsWith("kotlinx-locale-") }
            .orEmpty()
            .sortedBy(File::getName)

        var compared = 0
        for (module in modules) {
            val ilp32 = module.resolve("src/appleIlp32Main")
            val lp64 = module.resolve("src/appleLp64Main")
            if (!ilp32.isDirectory && !lp64.isDirectory) continue

            assertTrue(
                ilp32.isDirectory && lp64.isDirectory,
                "${module.name} has one width-specific Apple source set and not the other, " +
                    "so one set of targets has no implementation",
            )

            fun sources(root: File) = root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .associate { it.relativeTo(root).path to it.readText() }

            val left = sources(ilp32)
            val right = sources(lp64)
            assertEquals(left.keys, right.keys, "${module.name}: the two Apple source sets hold different files")
            for ((path, text) in left) {
                assertEquals(
                    text,
                    right[path],
                    "${module.name}/$path differs between appleIlp32Main and appleLp64Main. " +
                        "They exist only to be compiled against different pointer widths; " +
                        "the source is meant to be identical, so edit both or neither.",
                )
                compared++
            }
        }

        assertTrue(compared > 0, "no width-specific Apple sources found; has the layout changed?")
    }
}
