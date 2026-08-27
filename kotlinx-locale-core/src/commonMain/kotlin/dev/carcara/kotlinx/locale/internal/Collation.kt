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

package dev.carcara.kotlinx.locale.internal

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

/**
 * Sorting text the way a reader reads it, per UTS #10 and UTS #35 Part 5.
 *
 * Comparing strings by code point orders them by a number nobody sees: it puts
 * Ísland after Zimbabwe, Österreich after Zypern, and every accented initial at
 * the bottom of the list. The Unicode Collation Algorithm instead maps text to
 * collation elements and compares those in levels, so a difference in base
 * letter outranks a difference in accent, which outranks a difference in case.
 *
 * The order is not universal, which is the other half of the problem. Czech
 * sorts č as its own letter after c and ch after h; Icelandic ends its alphabet
 * with þ, æ and ö; Hungarian reads cs, dzs and zs as single letters. Those are
 * the per-locale tailorings, and the generator applies them over the root table
 * so this reads one table either way.
 *
 * Weights arrive already ranked as integers rather than as the byte sequences
 * FractionalUCA writes. Byte sequences are prefix-free so that they can be
 * concatenated, and a tailoring that inserts a letter between two others would
 * have to break that; ranking each distinct weight sidesteps it and compares the
 * same.
 */
@InternalKotlinxLocaleApi
public object Collation {

    /** A collation element: one weight per level, zero meaning ignorable. */
    internal class Elements(val primary: IntArray, val secondary: IntArray, val tertiary: IntArray)

    private var singles = HashMap<Int, Elements>()
    private var contractions = HashMap<String, Elements>()
    private var prefixes = HashMap<Long, Elements>()
    private var longestContraction = 1
    private var implicitBase = 0
    private var defaultSecondary = 0
    private var defaultTertiary = 0

    /**
     * Installs the root table. Until then every code point weighs by its own
     * value, which degrades to code point order rather than to anything wrong.
     */
    @InternalKotlinxLocaleApi
    public fun install(table: String) {
        if (singles.isNotEmpty() || table.isEmpty()) return
        val sections = table.split(SECTION)
        if (sections.size < 5) return

        implicitBase = sections[0].substringBefore(',').toInt(36)
        defaultSecondary = sections[0].substringAfter(',').substringBefore(',').toInt(36)
        defaultTertiary = sections[0].substringAfterLast(',').toInt(36)

        val single = HashMap<Int, Elements>()
        for (entry in sections[1].split(ENTRY)) {
            if (entry.isEmpty()) continue
            val colon = entry.indexOf(':')
            single[entry.substring(0, colon).toInt(36)] = decodeElements(entry.substring(colon + 1))
        }

        val many = HashMap<String, Elements>()
        var longest = 1
        for (entry in sections[2].split(ENTRY)) {
            if (entry.isEmpty()) continue
            val colon = entry.indexOf(':')
            val key = decodeKey(entry.substring(0, colon))
            many[key.first] = decodeElements(entry.substring(colon + 1))
            if (key.second > longest) longest = key.second
        }

        val prefixed = HashMap<Long, Elements>()
        for (entry in sections[3].split(ENTRY)) {
            if (entry.isEmpty()) continue
            val colon = entry.indexOf(':')
            val dot = entry.indexOf('.')
            val before = entry.substring(0, dot).toInt(36).toLong()
            val at = entry.substring(dot + 1, colon).toInt(36).toLong()
            prefixed[before shl 21 or at] = decodeElements(entry.substring(colon + 1))
        }

        singles = single
        contractions = many
        prefixes = prefixed
        longestContraction = longest
    }

    /**
     * Overlays a locale's tailoring on the installed root.
     *
     * A tailoring is a few dozen entries, so it ships as a delta rather than as
     * a second copy of a table with a hundred thousand rows in it.
     */
    @InternalKotlinxLocaleApi
    public fun tailored(delta: String): Tailored {
        val single = HashMap(singles)
        val many = HashMap(contractions)
        val prefixed = HashMap(prefixes)
        var longest = longestContraction
        if (delta.isNotEmpty()) {
            val sections = delta.split(SECTION)
            if (sections.size >= 3) {
                for (entry in sections[0].split(ENTRY)) {
                    if (entry.isEmpty()) continue
                    val colon = entry.indexOf(':')
                    single[entry.substring(0, colon).toInt(36)] = decodeElements(entry.substring(colon + 1))
                }
                for (entry in sections[1].split(ENTRY)) {
                    if (entry.isEmpty()) continue
                    val colon = entry.indexOf(':')
                    val key = decodeKey(entry.substring(0, colon))
                    many[key.first] = decodeElements(entry.substring(colon + 1))
                    if (key.second > longest) longest = key.second
                }
                for (entry in sections[2].split(ENTRY)) {
                    if (entry.isEmpty()) continue
                    val colon = entry.indexOf(':')
                    val dot = entry.indexOf('.')
                    val before = entry.substring(0, dot).toInt(36).toLong()
                    val at = entry.substring(dot + 1, colon).toInt(36).toLong()
                    prefixed[before shl 21 or at] = decodeElements(entry.substring(colon + 1))
                }
            }
        }
        return Tailored(single, many, prefixed, longest)
    }

    /** One locale's table: the root with its tailoring already folded in. */
    @InternalKotlinxLocaleApi
    public class Tailored internal constructor(
        private val singles: Map<Int, Elements>,
        private val contractions: Map<String, Elements>,
        private val prefixes: Map<Long, Elements>,
        private val longestContraction: Int,
    ) : Comparator<String> {

        override fun compare(a: String, b: String): Int {
            if (a == b) return 0
            val left = sortKey(a)
            val right = sortKey(b)
            val shared = if (left.size < right.size) left.size else right.size
            for (i in 0 until shared) {
                if (left[i] != right[i]) return if (left[i] < right[i]) -1 else 1
            }
            return left.size.compareTo(right.size)
        }

        /**
         * The sort key: every non-zero primary, then a separator, then the
         * secondaries, then the tertiaries. Comparing two keys compares the
         * base letters first and only looks at accents where the letters agree,
         * which is what makes resume and résumé neighbours rather than strangers.
         */
        public fun sortKey(text: String): IntArray {
            val elements = elementsFor(Normalization.decompose(text))
            val key = ArrayList<Int>(elements.size * 3 + 2)
            for (level in 0 until 3) {
                for (element in elements) {
                    val weights = when (level) {
                        0 -> element.primary
                        1 -> element.secondary
                        else -> element.tertiary
                    }
                    for (weight in weights) if (weight != 0) key.add(weight)
                }
                if (level < 2) key.add(0)
            }
            return key.toIntArray()
        }

        private fun elementsFor(decomposed: IntArray): List<Elements> {
            val points = decomposed.toMutableList()
            val out = ArrayList<Elements>(points.size + 4)
            var i = 0
            while (i < points.size) {
                // A prefix rule wins over the character's own entry: U+00B7 has
                // weights of its own, but after an l it takes the ones that make
                // l-middle-dot weigh the same as U+0140.
                if (i > 0) {
                    val prefixed = prefixes[points[i - 1].toLong() shl 21 or points[i].toLong()]
                    if (prefixed != null) {
                        out.add(prefixed); i++; continue
                    }
                }
                var matched: Elements? = null
                var length = 0
                var take = if (longestContraction < points.size - i) longestContraction else points.size - i
                while (take > 1) {
                    val candidate = contractions[keyOf(points, i, take)]
                    if (candidate != null) {
                        matched = candidate; length = take; break
                    }
                    take--
                }
                if (matched == null) {
                    matched = singles[points[i]]
                    length = 1
                }
                val found = matched
                if (found == null) {
                    out.add(implicitFor(points[i])); i++; continue
                }
                var resolved: Elements = found
                // A following non-starter joins the contraction when nothing
                // between them blocks it, which is what puts the Tibetan and
                // Devanagari sequences in the right place whatever order the
                // marks arrived in.
                // The key grows as marks are absorbed: a second non-starter has to
                // be looked up against the contraction that already swallowed the
                // first, not against the original span.
                var accumulated = keyOf(points, i, length)
                var j = i + length
                var lastClass = 0
                while (j < points.size) {
                    val combining = Normalization.combiningClass(points[j])
                    if (combining == 0) break
                    if (combining > lastClass) {
                        val extended = accumulated + encodePoint(points[j])
                        val candidate = contractions[extended]
                        if (candidate != null) {
                            accumulated = extended
                            resolved = candidate
                            points.removeAt(j)
                            continue
                        }
                    }
                    lastClass = combining
                    j++
                }
                out.add(resolved)
                i += length
            }
            return out
        }

        private fun keyOf(points: List<Int>, from: Int, length: Int): String =
            buildString(length * 2) { for (k in from until from + length) appendPoint(points[k]) }

        public companion object {}
    }

    private fun implicitFor(codePoint: Int): Elements =
        Elements(intArrayOf(implicitBase + codePoint), intArrayOf(defaultSecondary), intArrayOf(defaultTertiary))

    private fun decodeKey(encoded: String): Pair<String, Int> {
        val parts = encoded.split('.')
        return buildString { for (p in parts) appendPoint(p.toInt(36)) } to parts.size
    }

    /**
     * A code point as a fixed pair of chars, high half then low.
     *
     * Not `toChar()`: anything above the basic plane would lose its top bits, and
     * a contraction key made of truncated code points collides with a different
     * sequence rather than failing to match.
     */
    private fun StringBuilder.appendPoint(codePoint: Int) {
        append(((codePoint ushr 16) and 0xFFFF).toChar())
        append((codePoint and 0xFFFF).toChar())
    }

    private fun encodePoint(codePoint: Int): String =
        buildString(2) { appendPoint(codePoint) }

    private fun decodeElements(encoded: String): Elements {
        val levels = encoded.split('/')
        return Elements(decodeLevel(levels[0]), decodeLevel(levels[1]), decodeLevel(levels[2]))
    }

    private fun decodeLevel(encoded: String): IntArray {
        if (encoded.isEmpty()) return EMPTY
        val parts = encoded.split('.')
        return IntArray(parts.size) { parts[it].toInt(36) }
    }

    private val EMPTY = IntArray(0)
    private const val SECTION = ';'
    private const val ENTRY = ','
}
