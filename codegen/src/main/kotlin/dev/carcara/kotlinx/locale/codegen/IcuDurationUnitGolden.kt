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

import com.ibm.icu.number.NumberFormatter
import com.ibm.icu.number.Precision
import com.ibm.icu.util.MeasureUnit
import com.ibm.icu.util.ULocale
import java.io.File

/**
 * Goldens for duration units, asked of ICU's own `NumberFormatter`.
 *
 * The question this fixture settles is the fallback, not the wording. Nine
 * thousand `↑↑↑` markers sit in the `duration-*` blocks of release-48-2, and what
 * each one resolves to is stated nowhere a build can read: root carries only a
 * short block, so a missing short reads out of root while a missing long has to
 * come from the locale's own short, and a locale that declares nothing at all
 * falls through to English rather than to root's placeholders. Every one of those
 * three rules was read off this fixture rather than out of UTS #35, which is
 * exactly why it is a fixture and not a comment.
 *
 * It also pins the grammatical case filter. Serbian writes four forms of the same
 * unit pattern and only the caseless one is the citation form, so dropping the
 * `case` attribute is the difference between `3 сата` and `3 сати`.
 *
 * Scoped to [ICU_GOLDEN_LOCALES] rather than to every locale, and the reason is
 * in [Flattener.resolveDurationUnits]: past the major locales ICU's answers
 * follow its own coverage pruning rather than CLDR, and conforming to those would
 * mean shipping less wording than CLDR carries.
 */

/** The value the goldens are taken at, and the fraction digits it is shown with. */
private val GOLDEN_VALUES = listOf(0L, 1L, 2L, 3L, 5L, 11L, 21L, 100L)

class IcuDurationUnitGoldenEntry(val tag: String, val unit: String, val width: String, val value: Long, val text: String)

private val WIDTHS = listOf(
    "LONG" to NumberFormatter.UnitWidth.FULL_NAME,
    "SHORT" to NumberFormatter.UnitWidth.SHORT,
    "NARROW" to NumberFormatter.UnitWidth.NARROW,
)

fun extractIcuDurationUnitGolden(): List<IcuDurationUnitGoldenEntry> {
    ULocale.setDefault(ULocale.ROOT)
    java.util.Locale.setDefault(java.util.Locale.ROOT)

    val entries = ArrayList<IcuDurationUnitGoldenEntry>()
    for (tag in ICU_GOLDEN_LOCALES) {
        val locale = ULocale(tag)
        for (cldrType in DURATION_UNITS) {
            // ICU names the unit without the category prefix CLDR keys it by.
            val unit = MeasureUnit.forIdentifier(cldrType.removePrefix("duration-"))
            for ((widthName, width) in WIDTHS) {
                for (value in GOLDEN_VALUES) {
                    val text = NumberFormatter.withLocale(locale)
                        .unit(unit)
                        .unitWidth(width)
                        // Pinned so the goldens carry no fraction digits, matching
                        // what a Long formats to on this side.
                        .precision(Precision.integer())
                        .format(value)
                        .toString()
                    entries.add(IcuDurationUnitGoldenEntry(canonicalTag(tag), cldrType, widthName, value, text))
                }
            }
        }
    }

    val expected = ICU_GOLDEN_LOCALES.size * DURATION_UNITS.size * WIDTHS.size * GOLDEN_VALUES.size
    check(entries.size == expected) { "expected $expected duration unit goldens, got ${entries.size}" }
    // The canary. If the three widths ever agree in English the width plumbing
    // has collapsed, and every assertion downstream would still pass.
    val english = entries.filter { it.tag == "en" && it.unit == "duration-hour" && it.value == 2L }
    check(english.map { it.text } == listOf("2 hours", "2 hr", "2h")) {
        "ICU should write two English hours three ways, got ${english.map { it.text }}"
    }
    println("[codegen] duration unit goldens: ${entries.size} cells over ${ICU_GOLDEN_LOCALES.size} locales")
    return entries
}

/**
 * Written the way the interval goldens are: one entry per locale carrying its
 * answers joined into a single string, positional against a shared case list.
 *
 * Ten thousand `arrayOf` literals in one file is not a table, it is a static
 * initialiser past the JVM's 64K method limit, which fails at class generation
 * rather than at a size check.
 */
fun emitIcuDurationUnitGolden(outputFile: File, icuTag: String, entries: List<IcuDurationUnitGoldenEntry>) {
    outputFile.parentFile.mkdirs()
    val cases = entries.map { Triple(it.unit, it.width, it.value) }.distinct()
    val byTag = entries.groupBy(IcuDurationUnitGoldenEntry::tag)
    outputFile.writeText(
        buildString {
            append(LICENSE_HEADER + "// GENERATED by :codegen from ICU $icuTag. Do not edit.\n")
            append("// Regenerate with: ./gradlew :codegen:generateLocaleData\n")
            append("package dev.carcara.kotlinx.locale.datetime.cldr.durations.conformance\n\n")
            append("public const val ICU_DURATION_UNIT_GOLDEN_VERSION: String = \"${kotlinEscape(icuTag)}\"\n\n")
            append("/** CLDR unit type, width name and value, in the order every locale answers. */\n")
            append("public val icuDurationUnitGoldenCases: List<Triple<String, String, Long>> = listOf(\n")
            for ((unit, width, value) in cases) {
                append("    Triple(\"${kotlinEscape(unit)}\", \"$width\", ${value}L),\n")
            }
            append(")\n\n")
            append("/** Locale tag to its answers, positional against [icuDurationUnitGoldenCases]. */\n")
            append("public val icuDurationUnitGolden: Map<String, List<String>> =\n")
            append("    buildMap(${byTag.size}) {\n")
            for ((tag, rows) in byTag) {
                check(rows.size == cases.size) { "$tag answered ${rows.size} of ${cases.size} cases" }
                append("        put(\"${kotlinEscape(tag)}\", ")
                append("\"${kotlinEscape(rows.joinToString(LIST_SEPARATOR) { it.text })}\".split('\\u001E'))\n")
            }
            append("    }\n")
        },
    )
    println("[codegen] emitted ${entries.size} ICU duration unit goldens over ${byTag.size} locales to $outputFile")
}
