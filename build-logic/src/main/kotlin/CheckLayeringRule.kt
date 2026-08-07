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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Enforces the rule that lets any `-cldr-full` link against any `-types`:
 * hand-written code may name the generated enum types and their members, never
 * a specific entry.
 *
 * A `Currency.USD` compiled into an artifact shipped from Maven would fail to
 * link against a `-types` the Gradle plugin narrowed to a different entry set.
 * Generated sources are exempt, since they are the entry set. Tests are exempt,
 * since they never ship.
 */
@CacheableTask
abstract class CheckLayeringRule : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /**
     * A stamp, so the task participates in up-to-date checks and the build
     * cache. A verification task with no output re-reads every source on every
     * build, which is the wrong default for something in `check`.
     */
    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun check() {
        val entry = Regex("""\b(Country|Currency)\.([A-Z][A-Z0-9_]+)\b""")
        val root = rootDirectory.get().asFile
        val handWritten = sources.files.sorted().filterNot(::isGeneratedSource)
        val offenders = handWritten.flatMap { file ->
            blankComments(file.readText()).lineSequence().flatMapIndexed { index, line ->
                entry.findAll(line).map { "${file.relativeTo(root)}:${index + 1}: ${it.value}" }
            }.toList()
        }
        if (offenders.isEmpty()) {
            logger.lifecycle("[layering] ${handWritten.size} hand-written sources name no specific enum entry")
            stamp.get().asFile.apply {
                parentFile.mkdirs()
                writeText("${handWritten.size} sources checked\n")
            }
            return
        }
        error(
            buildString {
                appendLine("Hand-written code named a specific enum entry, which breaks the guarantee that")
                appendLine("any -cldr-full links against any -types. Use the enum type or its members instead,")
                appendLine("and leave the entries to generated code:")
                offenders.forEach { appendLine("  $it") }
            },
        )
    }

    /** Blanks out comments and string bodies, keeping line numbering intact. */
    private fun blankComments(text: String): String = buildString(text.length) {
        var index = 0
        var inBlock = false
        var inLine = false
        var inString = false
        while (index < text.length) {
            val ch = text[index]
            val next = text.getOrNull(index + 1)
            when {
                ch == '\n' -> {
                    inLine = false
                    append(ch)
                    index++
                }
                inLine -> {
                    append(' ')
                    index++
                }
                inBlock -> {
                    if (ch == '*' && next == '/') {
                        inBlock = false
                        append("  ")
                        index += 2
                    } else {
                        append(' ')
                        index++
                    }
                }
                inString -> {
                    if (ch == '\\') {
                        append("  ")
                        index += 2
                    } else {
                        if (ch == '"') inString = false
                        append(ch)
                        index++
                    }
                }
                ch == '/' && next == '/' -> {
                    inLine = true
                    append("  ")
                    index += 2
                }
                ch == '/' && next == '*' -> {
                    inBlock = true
                    append("  ")
                    index += 2
                }
                ch == '"' -> {
                    inString = true
                    append(ch)
                    index++
                }
                else -> {
                    append(ch)
                    index++
                }
            }
        }
    }
}
