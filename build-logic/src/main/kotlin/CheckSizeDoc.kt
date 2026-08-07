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

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Fails when the committed size document no longer describes the build.
 *
 * Compares scenarios by name exactly, so a probe added without regenerating the
 * document is a build failure rather than a row nobody notices is missing. The
 * numbers themselves are compared within a tolerance: a bundle size moves a
 * little with the toolchain and the host, and a byte-exact check would fail on
 * differences that mean nothing while teaching everyone to regenerate without
 * reading what changed. A figure that has gone properly stale still fails.
 */
@CacheableTask
abstract class CheckSizeDoc : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val generated: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val committed: RegularFileProperty

    /** How far a committed figure may drift before it counts as stale, in percent. */
    @get:Input
    abstract val tolerancePercent: Property<Int>

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun check() {
        val fresh = sizesIn(generated.get().asFile)
        val doc = sizesIn(committed.get().asFile)
        check(fresh.isNotEmpty()) { "the generated size document has no budget table; SizeReport wrote something unexpected" }
        val tolerance = tolerancePercent.get()

        val missing = fresh.keys - doc.keys
        val extra = doc.keys - fresh.keys
        val stale = fresh.filter { (scenario, measured) ->
            val documented = doc[scenario] ?: return@filter false
            val allowed = measured.bytes * tolerance / 100
            documented.bytes < measured.bytes - allowed || documented.bytes > measured.bytes + allowed
        }

        val problems = buildList {
            if (missing.isNotEmpty()) add("not in the document at all: ${missing.sorted()}")
            if (extra.isNotEmpty()) add("in the document but no longer built: ${extra.sorted()}")
            for ((scenario, measured) in stale) {
                // Each side's own printed figure, not one re-derived from bytes: a
                // message off by a tenth sends the reader looking for a difference
                // that is only this task's rounding.
                add("$scenario is ${measured.text} but the document says ${doc.getValue(scenario).text}")
            }
        }
        check(problems.isEmpty()) {
            "docs/size.md is out of date. Run ./gradlew updateSizeDoc.\n" +
                problems.sorted().joinToString("\n") { "  - $it" }
        }

        stamp.get().asFile.apply {
            parentFile.mkdirs()
            writeText("${fresh.size} scenarios within $tolerance%\n")
        }
    }

    /**
     * Reads `| scenario | 20.2 KB | ...` rows from the budget table only.
     *
     * Everything after the first `##` is prose and the pairing table, which
     * restates the same measurements under domain names rather than probe names.
     * One table is the source of the figures, so only one is read.
     */
    private fun sizesIn(file: File): Map<String, Measurement> =
        file.readLines()
            .takeWhile { !it.startsWith("## ") }
            .mapNotNull { line -> ROW.matchEntire(line.trim())?.destructured }
            .associate { (scenario, whole, tenths) ->
                scenario to Measurement(
                    bytes = (whole.toLong() * 10 + tenths.toLong()) * 1024 / 10,
                    text = "$whole.$tenths KB",
                )
            }

    private data class Measurement(val bytes: Long, val text: String)

    private companion object {
        val ROW = Regex("""\|\s*([a-z][a-z0-9-]*)\s*\|\s*(\d+)\.(\d) KB\s*\|.*""")
    }
}
