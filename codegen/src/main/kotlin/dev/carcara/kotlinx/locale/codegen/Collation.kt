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
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The CLDR root collation table and the per-locale tailorings over it.
 *
 * The source is `FractionalUCA.txt` rather than `allkeys_CLDR.txt`, and the
 * difference is not a preference. allkeys cannot express the root order: it
 * lists no Han at all, Han sorts below the Kangxi radicals, and every implicit
 * weight scheme puts unlisted characters above every listed one. The
 * radical-stroke order that actually places Han is in FractionalUCA's
 * `[radical ...]` lines, which is why ICU builds from that file too.
 *
 * Weights come out of here as ranked integers rather than as the byte sequences
 * the file writes. Those sequences are prefix-free so a sort key can concatenate
 * them, and a tailoring inserting a letter between two others would have to
 * break that; ranking each distinct weight sidesteps it and orders the same.
 */
class CollationElement(val primary: List<Int>, val secondary: List<Int>, val tertiary: List<Int>)

class CollationRoot(
    val entries: Map<List<Int>, List<CollationElement>>,
    val prefixed: Map<Pair<Int, Int>, List<CollationElement>>,
    val hanOrder: List<Int>,
)

private val WEIGHT_ORDER = Comparator<List<Int>> { a, b ->
    var i = 0
    while (i < a.size && i < b.size) {
        if (a[i] != b[i]) return@Comparator a[i].compareTo(b[i])
        i++
    }
    a.size.compareTo(b.size)
}

private fun parseWeight(part: String): List<Int> {
    val trimmed = part.trim()
    if (trimmed.isEmpty()) return emptyList()
    return trimmed.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16) }
}

/** Reads `FractionalUCA.txt`: the root weights, the prefix rules and the Han order. */
fun parseFractionalUca(file: File): CollationRoot {
    val entries = LinkedHashMap<List<Int>, MutableList<CollationElement?>>()
    val prefixed = LinkedHashMap<Pair<Int, Int>, List<CollationElement>>()
    val hanOrder = ArrayList<Int>()
    // Han weights are referenced before the radical lines have been read, so the
    // references are filled in once the order is known.
    // key -> (index in that key's element list, referenced Han code point, tertiary)
    val deferred = ArrayList<Pair<List<Int>, Triple<Int, Int, List<Int>>>>()

    file.forEachLine { raw ->
        when {
            raw.startsWith("[radical ") -> {
                val body = raw.removePrefix("[radical ").removeSuffix("]")
                if (!body.startsWith("end")) {
                    val chars = body.substringAfter(':')
                    var i = 0
                    while (i < chars.length) {
                        val point = chars.codePointAt(i)
                        val width = Character.charCount(point)
                        val next = i + width
                        if (next < chars.length && chars[next] == '-') {
                            val to = chars.codePointAt(next + 1)
                            for (cp in point..to) hanOrder.add(cp)
                            i = next + 1 + Character.charCount(to)
                        } else {
                            hanOrder.add(point)
                            i = next
                        }
                    }
                }
            }
            raw.startsWith("[") || raw.startsWith("#") || raw.isBlank() -> Unit
            else -> {
                val head = raw.substringBefore(';')
                val body = raw.substringAfter(';', "").substringBefore('#').trim()
                if (body.isNotEmpty()) {
                    val elements = ArrayList<CollationElement?>()
                    val pending = ArrayList<Triple<Int, Int, List<Int>>>()
                    for (chunk in body.split(']')) {
                        val piece = chunk.trim()
                        if (!piece.startsWith("[")) continue
                        val parts = piece.substring(1).split(',')
                        val first = parts[0].trim()
                        if (first.startsWith("U+")) {
                            val reference = first.substring(2).toInt(16)
                            if (parts.size >= 3) {
                                // [U+X, s, t] is the Han element for X followed by a
                                // secondary-only element, which is how the CJK radicals
                                // expand to an ideograph plus their radical mark.
                                pending.add(Triple(elements.size, reference, listOf(0x05)))
                                elements.add(null)
                                elements.add(
                                    CollationElement(
                                        emptyList(),
                                        parseWeight(parts[1]),
                                        parseWeight(parts[2]).map { it and 0x3F },
                                    ),
                                )
                            } else {
                                val tertiary = if (parts.size > 1) parseWeight(parts[1]) else listOf(0x05)
                                pending.add(Triple(elements.size, reference, tertiary.map { it and 0x3F }))
                                elements.add(null)
                            }
                            continue
                        }
                        if (parts.size != 3) continue
                        elements.add(
                            CollationElement(
                                parseWeight(parts[0]),
                                parseWeight(parts[1]),
                                parseWeight(parts[2]).map { it and 0x3F },
                            ),
                        )
                    }
                    if (elements.isNotEmpty()) {
                        if (head.contains('|')) {
                            val before = head.substringBefore('|').trim().toInt(16)
                            val at = head.substringAfter('|').trim().toInt(16)
                            prefixed[before to at] = elements.filterNotNull()
                        } else {
                            val key = head.trim().split(' ').filter { it.isNotEmpty() }.map { it.toInt(16) }
                            // FDD0..FDEF are CLDR's internal anchors, not text weights.
                            if (key.isNotEmpty() && key[0] !in 0xFDD0..0xFDEF) {
                                entries[key] = elements
                                for (item in pending) deferred.add(key to item)
                            }
                        }
                    }
                }
            }
        }
    }

    // Insertion-ordered: the emitted table has to carry Han in radical-stroke
    // order, and a HashMap would scatter a hundred thousand entries by hash.
    val hanWeight = LinkedHashMap<Int, List<Int>>(hanOrder.size)
    hanOrder.forEachIndexed { index, cp ->
        hanWeight[cp] = listOf(0x81 + index / (254 * 254), 2 + index / 254 % 254, 2 + index % 254)
    }
    for ((key, item) in deferred) {
        val (index, reference, tertiary) = item
        val list = entries[key] ?: continue
        list[index] = CollationElement(hanWeight[reference] ?: listOf(0xE0), listOf(0x05), tertiary)
    }

    val resolved = entries.mapValues { (_, v) -> v.filterNotNull() }
    val withHan = LinkedHashMap<List<Int>, List<CollationElement>>(resolved)
    for ((cp, weight) in hanWeight) {
        withHan[listOf(cp)] = listOf(CollationElement(weight, listOf(0x05), listOf(0x05)))
    }
    return CollationRoot(withHan, prefixed, hanOrder)
}

/** Distinct weights ranked as integers, with room left between them to tailor into. */
class WeightRanks(
    val primary: Map<List<Int>, Int>,
    val secondary: Map<List<Int>, Int>,
    val tertiary: Map<List<Int>, Int>,
    val implicitBase: Int,
) {
    companion object {
        /**
         * Room between neighbouring weights for a tailoring to insert into.
         *
         * The primary level carries a hundred and thirty eight thousand distinct
         * weights, so its gap is the one that has to stay small enough to keep
         * every rank inside Int. The secondary and tertiary levels carry two
         * hundred and sixty eight and thirty, and they are where the chains bite:
         * a rule like Croatian's `&D<dž<<<ǆ<<<Dž<<<ǅ<<<DŽ<<<Ǆ` mints repeatedly
         * from the same anchor, and every later chain subdivides the same gap
         * again, so a narrow one closes long before the rules run out.
         */
        const val BASE_GAP: Int = 1024
        const val LEVEL_GAP: Int = 1 shl 16

        /**
         * The implicit band needs one gap of its own, wide enough for every code
         * point. It goes after the last regular weight rather than past the end:
         * top bytes E5..EF are TRAILING and F0..FF are SPECIAL, so U+FFFD sorts
         * above every implicit weight and below nothing.
         */
        const val IMPLICIT_ROOM: Int = 1 shl 21

        fun of(root: CollationRoot): WeightRanks {
            val primaries = sortedSetOf(WEIGHT_ORDER)
            val secondaries = sortedSetOf(WEIGHT_ORDER)
            val tertiaries = sortedSetOf(WEIGHT_ORDER)
            for (list in root.entries.values + root.prefixed.values) {
                for (element in list) {
                    if (element.primary.isNotEmpty()) primaries.add(element.primary)
                    if (element.secondary.isNotEmpty()) secondaries.add(element.secondary)
                    if (element.tertiary.isNotEmpty()) tertiaries.add(element.tertiary)
                }
            }
            val lastRegular = primaries.last { it[0] < 0xE0 }
            val primaryRanks = LinkedHashMap<List<Int>, Int>(primaries.size)
            var run = 0
            for (weight in primaries) {
                run += BASE_GAP
                primaryRanks[weight] = run
                if (weight == lastRegular) run += IMPLICIT_ROOM
            }
            fun ranked(values: Collection<List<Int>>) =
                values.withIndex().associate { (i, v) -> v to (i + 1) * LEVEL_GAP }
            return WeightRanks(
                primaryRanks,
                ranked(secondaries),
                ranked(tertiaries),
                primaryRanks.getValue(lastRegular) + 1,
            )
        }
    }

    fun defaultSecondary(): Int = secondary.getValue(listOf(0x05))
    fun defaultTertiary(): Int = tertiary.getValue(listOf(0x05))

    fun rank(element: CollationElement): IntArray = intArrayOf(
        if (element.primary.isEmpty()) 0 else primary.getValue(element.primary),
        if (element.secondary.isEmpty()) 0 else secondary.getValue(element.secondary),
        if (element.tertiary.isEmpty()) 0 else tertiary.getValue(element.tertiary),
    )
}

private fun base36(value: Int): String = value.toString(36)

private fun encodeElements(ranked: List<IntArray>): String {
    val levels = Array(3) { level -> ranked.mapNotNull { if (it[level] != 0) base36(it[level]) else null } }
    return levels.joinToString("/") { it.joinToString(".") }
}

/** The root table in the form `Collation.install` reads. */
fun encodeCollationRoot(root: CollationRoot, ranks: WeightRanks): String {
    val singles = ArrayList<String>()
    val contractions = ArrayList<String>()
    for ((key, elements) in root.entries) {
        val encoded = encodeElements(elements.map { ranks.rank(it) })
        if (key.size == 1) singles.add(base36(key[0]) + ":" + encoded)
        else contractions.add(key.joinToString(".") { base36(it) } + ":" + encoded)
    }
    val prefixes = root.prefixed.map { (pair, elements) ->
        base36(pair.first) + "." + base36(pair.second) + ":" + encodeElements(elements.map { ranks.rank(it) })
    }
    val header = base36(ranks.implicitBase) + "," + base36(ranks.defaultSecondary()) + "," + base36(ranks.defaultTertiary())
    return listOf(header, singles.joinToString(","), contractions.joinToString(","), prefixes.joinToString(","), "")
        .joinToString(";")
}

/** The `<cr>` rule text of one collation type in a CLDR locale file. */
fun collationRules(file: File, type: String = "standard"): String {
    // CLDR's locale files declare the LDML DTD, which is not beside them and is
    // not needed to read the rules.
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isValidating = false
    }
    val document = factory.newDocumentBuilder().parse(file)
    val collations = document.getElementsByTagName("collation")
    for (i in 0 until collations.length) {
        val node = collations.item(i)
        if (node.attributes?.getNamedItem("type")?.nodeValue != type) continue
        val children = node.childNodes
        for (j in 0 until children.length) {
            val child = children.item(j)
            if (child.nodeName == "cr") return child.textContent ?: ""
        }
    }
    return ""
}
