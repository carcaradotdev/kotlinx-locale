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

package dev.carcara.kotlinx.locale.size

import java.io.File

/**
 * Measures what the generated data weighs, now and against a git ref.
 *
 * `report <root> <out>` writes the document. `compare <root> <ref>` prints what
 * changed since that ref, which is the question a codec has to answer: a table
 * that got smaller on one platform and larger on another is the normal case,
 * not the exception, so all three units are always shown.
 */
public fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "report" -> {
            val root = File(args[1])
            val out = File(args[2])
            val sources = readWorkingTree(root)
            val byPlatform = GeneratedData.PLATFORMS.mapValues { (_, sets) ->
                GeneratedData.tablesOf(sources, sets)
            }
            out.parentFile?.mkdirs()
            out.writeText(DataSizeDocument.render(byPlatform))
            println("[size] ${byPlatform.values.first().size} tables measured into $out")
        }

        "compare" -> {
            val root = File(args[1])
            val ref = args.getOrElse(2) { "origin/main" }
            // Both sides read through the same platform selection, so a
            // comparison against a ref that packed its data differently still
            // compares what one platform paid then against what it pays now.
            val platform = GeneratedData.PLATFORMS.getValue("Android, JVM")
            val after = GeneratedData.tablesOf(readWorkingTree(root), platform)
            val before = GeneratedData.tablesOf(readGitRef(root, ref), platform + "utf8Main")
            println(DataSizeDocument.renderComparison(ref, before, after))
        }

        else -> error("usage: report <root> <out> | compare <root> [ref]")
    }
}

/** Every Kotlin source under the checkout, keyed by its path relative to [root]. */
internal fun readWorkingTree(root: File): Map<String, String> = root
    .walkTopDown()
    .onEnter { it.name != "build" && it.name != ".git" && it.name != "node_modules" }
    .filter { it.isFile && it.extension == "kt" }
    .associate { it.relativeTo(root).path to it.readText() }

/**
 * The same, read out of a git ref rather than the disk.
 *
 * `git show` per file rather than a checkout, so comparing against a branch
 * costs nothing and cannot disturb the working tree.
 */
internal fun readGitRef(root: File, ref: String): Map<String, String> {
    val names = run(root, "git", "ls-tree", "-r", "--name-only", ref)
        .lineSequence()
        .filter { it.endsWith(".kt") && !it.startsWith("build/") }
        .toList()
    return names.associateWith { run(root, "git", "show", "$ref:$it") }
}

private fun run(root: File, vararg command: String): String {
    val process = ProcessBuilder(*command)
        .directory(root)
        .redirectErrorStream(false)
        .start()
    val out = process.inputStream.bufferedReader().readText()
    val code = process.waitFor()
    check(code == 0) { "${command.joinToString(" ")} failed with $code" }
    return out
}
