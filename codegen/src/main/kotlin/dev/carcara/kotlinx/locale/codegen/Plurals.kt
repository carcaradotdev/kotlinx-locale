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

/** One `@integer` or `@decimal` sample CLDR writes next to a rule, for the conformance fixture. */
class PluralSample(val locales: String, val category: String, val value: String, val isOrdinal: Boolean)

class PluralData(
    /** rule set id -> encoded `category:condition` pairs joined by `;`. */
    val ruleSets: Map<String, String>,
    /** CLDR locale id -> `"<cardinalId> <ordinalId>"`. */
    val index: Map<String, String>,
    val samples: List<PluralSample>,
)

/**
 * Reads `plurals.xml` and `ordinals.xml` into rule sets shared by id.
 *
 * 1122 locales use about sixty-five distinct rule sets between the two files, so
 * the tables are keyed by rule set and the index maps a locale to the pair it
 * uses. That is 4 KB of data for every locale in CLDR, which is why the Gradle
 * plugin carries it whole rather than narrowing it: dropping rows would save
 * nothing and would turn an unlisted locale into wrong grammar rather than an
 * error.
 *
 * The conditions keep CLDR's own syntax verbatim, with only the sample lists
 * stripped and whitespace collapsed. The runtime parses that syntax, so the
 * generated table can be diffed against `plurals.xml` by eye, and CLDR's samples
 * become a fixture that tests the shipped evaluator rather than a generator-side
 * reimplementation.
 */
fun parsePlurals(cldrDir: File): PluralData {
    val ruleSets = LinkedHashMap<String, String>()
    val cardinal = LinkedHashMap<String, String>()
    val ordinal = LinkedHashMap<String, String>()
    val samples = ArrayList<PluralSample>()

    fun read(file: File, prefix: String, isOrdinal: Boolean, into: MutableMap<String, String>) {
        val root = parseXml(file).documentElement
        val plurals = root.child("plurals") ?: error("${file.name}: no <plurals>")
        var counter = 0
        for (group in plurals.childElements("pluralRules")) {
            val locales = group.getAttribute("locales").trim()
            if (locales.isEmpty()) continue
            val encoded = StringBuilder()
            for (rule in group.childElements("pluralRule")) {
                val category = rule.getAttribute("count")
                val body = rule.textContent.orEmpty()
                val condition = body.substringBefore('@').trim().replace(Regex("\\s+"), " ")
                for (sample in parseSamples(body)) {
                    samples += PluralSample(locales, category, sample, isOrdinal)
                }
                if (category == "other" || condition.isEmpty()) continue
                if (encoded.isNotEmpty()) encoded.append(';')
                encoded.append(category).append(':').append(condition)
            }
            val id = "$prefix${counter++}"
            ruleSets[id] = encoded.toString()
            for (locale in locales.split(' ')) {
                if (locale.isNotEmpty()) into[locale] = id
            }
        }
    }

    read(cldrDir.resolve("common/supplemental/plurals.xml"), "c", isOrdinal = false, into = cardinal)
    read(cldrDir.resolve("common/supplemental/ordinals.xml"), "o", isOrdinal = true, into = ordinal)

    val index = LinkedHashMap<String, String>()
    for (locale in (cardinal.keys + ordinal.keys).sorted()) {
        index[locale] = "${cardinal[locale].orEmpty()} ${ordinal[locale].orEmpty()}"
    }

    check(cardinal.isNotEmpty() && ordinal.isNotEmpty()) { "plural rules resolved to nothing" }
    return PluralData(ruleSets, index, samples)
}

/** The `@integer 1, 2, …` and `@decimal 1.0~1.5, …` lists, with the `…` and ranges expanded to their ends. */
private fun parseSamples(body: String): List<String> {
    val result = ArrayList<String>()
    for (marker in listOf("@integer", "@decimal")) {
        val start = body.indexOf(marker)
        if (start < 0) continue
        val end = body.drop(start + marker.length).indexOfFirst { it == '@' }
        val text = if (end <
            0
        ) {
            body.substring(start + marker.length)
        } else {
            body.substring(start + marker.length, start + marker.length + end)
        }
        for (raw in text.split(',')) {
            val value = raw.trim()
            if (value.isEmpty() || value == "…" || value == "...") continue
            // A `1.0~1.5` range: take both ends rather than every step, which is
            // enough to exercise the rule and keeps the fixture small.
            if ('~' in value) {
                result += value.substringBefore('~').trim()
                result += value.substringAfter('~').trim()
            } else {
                result += value
            }
        }
    }
    // Compact samples carry a `c` exponent this fixture does not exercise, so
    // they are dropped rather than mis-read as plain decimals.
    return result.filter { value -> value.isNotEmpty() && value.all { it.isDigit() || it == '.' } }
}
