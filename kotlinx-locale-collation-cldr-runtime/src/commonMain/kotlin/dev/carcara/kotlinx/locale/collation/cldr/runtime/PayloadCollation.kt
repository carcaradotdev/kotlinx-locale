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

package dev.carcara.kotlinx.locale.collation.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.collation.CollationStrength
import dev.carcara.kotlinx.locale.internal.Normalization

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
public object PayloadCollation {

    /** A collation element: one weight per level, zero meaning ignorable. */
    internal class Elements(val primary: IntArray, val secondary: IntArray, val tertiary: IntArray)

    /**
     * The Han ideographs, as spans rather than as entries.
     *
     * A hundred and two thousand of the root table's entries are ideographs, and
     * each one carries a primary weight that is a function of its place in the
     * radical-stroke order and nothing else. The generator writes the places
     * where both the code point and the weight run on together, so this reads
     * three parallel arrays and computes the rest. It is the difference between
     * 2.19 million characters of table and a hundred and forty thousand.
     *
     * Sorted by [hanStart], so a lookup is a binary search.
     */
    private var hanStart = IntArray(0)
    private var hanLength = IntArray(0)
    private var hanRank = IntArray(0)

    private var singles = HashMap<Int, Elements>()
    private var contractions = HashMap<String, Elements>()
    private var prefixes = HashMap<Long, Elements>()
    private var longestContraction = 1
    private var implicitBase = 0
    private var defaultSecondary = 0
    private var defaultTertiary = 0

    /** How far apart two neighbouring ranks sit, which is what a span steps by. */
    private var rankStride = 0

    /**
     * Installs the root table. Until then every code point weighs by its own
     * value, which degrades to code point order rather than to anything wrong.
     */
    @InternalKotlinxLocaleApi
    public fun install(table: String) {
        if (singles.isNotEmpty() || table.isEmpty()) return
        val sections = table.split(SECTION)
        if (sections.size < 5) return

        val header = sections[0].split(ENTRY)
        if (header.size < 4) return
        implicitBase = header[0].toInt(36)
        defaultSecondary = header[1].toInt(36)
        defaultTertiary = header[2].toInt(36)
        rankStride = header[3].toInt(36)

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

        // Deltas on both climbing columns, so the values written down stay short
        // where the numbers themselves do not.
        val spans = sections[4].split(ENTRY).filter(String::isNotEmpty)
        val starts = IntArray(spans.size)
        val lengths = IntArray(spans.size)
        val firstRanks = IntArray(spans.size)
        var codePoint = 0
        var rank = 0
        for ((index, span) in spans.withIndex()) {
            val first = span.indexOf('.')
            val second = span.indexOf('.', first + 1)
            codePoint += span.substring(0, first).toInt(36)
            rank += span.substring(second + 1).toInt(36)
            starts[index] = codePoint
            lengths[index] = span.substring(first + 1, second).toInt(36)
            firstRanks[index] = rank
        }

        singles = single
        contractions = many
        prefixes = prefixed
        longestContraction = longest
        hanStart = starts
        hanLength = lengths
        hanRank = firstRanks
    }

    /**
     * The rank of an ideograph, or zero when [codePoint] is not in a span.
     *
     * Zero rather than null so the lookup allocates nothing: a real rank is
     * always positive, because [WeightRanks] starts counting at one gap.
     */
    private fun hanRankOf(codePoint: Int): Int {
        var low = 0
        var high = hanStart.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val start = hanStart[mid]
            when {
                codePoint < start -> high = mid - 1
                codePoint >= start + hanLength[mid] -> low = mid + 1
                else -> return hanRank[mid] + (codePoint - start) * rankStride
            }
        }
        return 0
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
        var reorderFrom = IntArray(0)
        var reorderTo = IntArray(0)
        var reorderShift = IntArray(0)
        var caseFrom = IntArray(0)
        var caseTo = IntArray(0)
        var backwardsSecondary = false
        var suppressed: Set<Int> = emptySet()
        if (delta.isNotEmpty()) {
            val sections = delta.split(SECTION)
            if (sections.size >= 7) {
                // Upper-first exchanges two bands of tertiary weights, so the
                // pairs run both ways and one pass swaps rather than collapses.
                val swaps = sections[4].split(ENTRY).filter(String::isNotEmpty)
                caseFrom = IntArray(swaps.size)
                caseTo = IntArray(swaps.size)
                for ((index, swap) in swaps.withIndex()) {
                    val dot = swap.indexOf('.')
                    caseFrom[index] = swap.substring(0, dot).toInt(36)
                    caseTo[index] = swap.substring(dot + 1).toInt(36)
                }
                backwardsSecondary = sections[5] == "b"
                suppressed = sections[6].split(ENTRY).filter(String::isNotEmpty)
                    .mapTo(HashSet()) { it.toInt(36) }
            }
            if (sections.size >= 4) {
                // `[reorder Cyrl]` lifts a whole writing system above the others,
                // which moves a band of primary weights rather than any one
                // letter. Three parallel arrays of `(from, to, newFrom)`: a
                // primary inside a band shifts by a constant and one outside
                // every band does not move at all.
                val bands = sections[3].split(ENTRY).filter(String::isNotEmpty)
                reorderFrom = IntArray(bands.size)
                reorderTo = IntArray(bands.size)
                reorderShift = IntArray(bands.size)
                for ((index, band) in bands.withIndex()) {
                    val first = band.indexOf('.')
                    val second = band.indexOf('.', first + 1)
                    val from = band.substring(0, first).toInt(36)
                    reorderFrom[index] = from
                    reorderTo[index] = band.substring(first + 1, second).toInt(36)
                    reorderShift[index] = band.substring(second + 1).toInt(36) - from
                }
            }
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
        return Tailored(
            single,
            many,
            prefixed,
            longest,
            reorderFrom,
            reorderTo,
            reorderShift,
            caseFrom,
            caseTo,
            backwardsSecondary,
            suppressed,
        )
    }

    /** One locale's table: the root with its tailoring already folded in. */
    @InternalKotlinxLocaleApi
    public class Tailored internal constructor(
        private val singles: Map<Int, Elements>,
        private val contractions: Map<String, Elements>,
        private val prefixes: Map<Long, Elements>,
        private val longestContraction: Int,
        private val reorderFrom: IntArray,
        private val reorderTo: IntArray,
        private val reorderShift: IntArray,
        private val caseFrom: IntArray,
        private val caseTo: IntArray,
        /** Whether the secondary level reads back to front, for Canadian French. */
        private val backwardsSecondary: Boolean,
        /** Characters that never start a contraction in this locale. */
        private val suppressed: Set<Int>,
        /** How many levels a comparison reads, one to three. */
        private val levels: Int = 3,
    ) : Comparator<String> {

        /**
         * The same table, answering at [strength].
         *
         * A new view rather than a new table: the maps are shared, so asking the
         * same locale for a search comparator beside its sorting one costs three
         * references.
         */
        @InternalKotlinxLocaleApi
        public fun at(strength: CollationStrength): Tailored {
            val wanted = when (strength) {
                CollationStrength.PRIMARY -> 1
                CollationStrength.SECONDARY -> 2
                CollationStrength.TERTIARY -> 3
            }
            if (wanted == levels) return this
            return Tailored(
                singles,
                contractions,
                prefixes,
                longestContraction,
                reorderFrom,
                reorderTo,
                reorderShift,
                caseFrom,
                caseTo,
                backwardsSecondary,
                suppressed,
                wanted,
            )
        }

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

        /** A primary after the locale's script reordering, if it has one. */
        private fun reordered(primary: Int): Int {
            for (index in reorderFrom.indices) {
                if (primary >= reorderFrom[index] && primary <= reorderTo[index]) return primary + reorderShift[index]
            }
            return primary
        }

        /**
         * The sort key: every non-zero primary, then a separator, then the
         * secondaries, then the tertiaries. Comparing two keys compares the
         * base letters first and only looks at accents where the letters agree,
         * which is what makes resume and résumé neighbours rather than strangers.
         *
         * A key built at a lower strength stops early rather than comparing and
         * discarding: at PRIMARY there are no secondaries in it at all, so
         * resume and résumé produce the same key and compare equal.
         */
        public fun sortKey(text: String): IntArray {
            val elements = elementsFor(Normalization.decompose(text))
            val key = ArrayList<Int>(elements.size * levels + levels)
            val level1 = ArrayList<Int>()
            for (level in 0 until levels) {
                val into = if (level == 1 && backwardsSecondary) level1 else key
                for (element in elements) {
                    val weights = when (level) {
                        0 -> element.primary
                        1 -> element.secondary
                        else -> element.tertiary
                    }
                    for (weight in weights) {
                        if (weight == 0) continue
                        into.add(
                            when (level) {
                                0 -> reordered(weight)
                                2 -> cased(weight)
                                else -> weight
                            },
                        )
                    }
                }
                if (into === level1) {
                    // Canadian French orders by the last accent rather than the
                    // first, so this level is read back to front.
                    for (index in level1.indices.reversed()) key.add(level1[index])
                    level1.clear()
                }
                if (level < levels - 1) key.add(0)
            }
            return key.toIntArray()
        }

        /** A tertiary after the locale's case ordering, if it asked for one. */
        private fun cased(tertiary: Int): Int {
            for (index in caseFrom.indices) if (caseFrom[index] == tertiary) return caseTo[index]
            return tertiary
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
                        out.add(prefixed)
                        i++
                        continue
                    }
                }
                var matched: Elements? = null
                var length = 0
                // A suppressed character never starts a contraction: Serbian and
                // Macedonian ask for that so one letter does not swallow the next.
                var take = if (points[i] in suppressed) {
                    1
                } else if (longestContraction < points.size - i) {
                    longestContraction
                } else {
                    points.size - i
                }
                while (take > 1) {
                    val candidate = contractions[keyOf(points, i, take)]
                    if (candidate != null) {
                        matched = candidate
                        length = take
                        break
                    }
                    take--
                }
                if (matched == null) {
                    matched = singles[points[i]]
                    length = 1
                }
                val found = matched
                if (found == null) {
                    out.add(unlisted(points[i]))
                    i++
                    continue
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

    /**
     * What a character the table does not list weighs.
     *
     * An ideograph is looked up in the spans first: it is not listed, but its
     * weight is known exactly, and falling through to the implicit band would
     * put every Han character above every letter instead of in radical-stroke
     * order. Anything still unlisted lands in the implicit band, which orders by
     * code point and is what UTS #10 asks for.
     */
    private fun unlisted(codePoint: Int): Elements {
        val rank = hanRankOf(codePoint)
        if (rank != 0) return Elements(intArrayOf(rank), intArrayOf(defaultSecondary), intArrayOf(defaultTertiary))
        return Elements(intArrayOf(implicitBase + codePoint), intArrayOf(defaultSecondary), intArrayOf(defaultTertiary))
    }

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

    private fun encodePoint(codePoint: Int): String = buildString(2) { appendPoint(codePoint) }

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
