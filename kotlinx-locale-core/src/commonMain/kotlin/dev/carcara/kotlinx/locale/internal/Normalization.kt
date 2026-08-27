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
 * Canonical normalisation, per UAX #15.
 *
 * Two strings that a reader would call the same word can be spelled with
 * different code points: `Ä` is one code point or two, and both are correct
 * Unicode. Normalisation is what makes them one thing before anything compares
 * them, which is why UTS #10 states collation over the decomposed form.
 *
 * Neither the mappings nor the exclusions are invented here. Both come from the
 * vendored UCD files the generator reads, and the implementation is held to
 * Unicode's own `NormalizationTest.txt`, every case of it.
 *
 * Hangul is arithmetic rather than table data, exactly as UAX #15 section 3.12
 * writes it, so the syllables cost nothing to carry.
 */
@InternalKotlinxLocaleApi
public object Normalization {

    private const val HANGUL_S_BASE = 0xAC00
    private const val HANGUL_L_BASE = 0x1100
    private const val HANGUL_V_BASE = 0x1161
    private const val HANGUL_T_BASE = 0x11A7
    private const val HANGUL_L_COUNT = 19
    private const val HANGUL_V_COUNT = 21
    private const val HANGUL_T_COUNT = 28
    private const val HANGUL_N_COUNT = HANGUL_V_COUNT * HANGUL_T_COUNT
    private const val HANGUL_S_COUNT = HANGUL_L_COUNT * HANGUL_N_COUNT

    private var combiningClasses = HashMap<Int, Int>()
    private var decompositions = HashMap<Int, IntArray>()
    private var compositions = HashMap<Long, Int>()

    /**
     * Installs the normalisation tables.
     *
     * Called once by whichever generated artifact carries them. Until then every
     * code point reads as its own decomposition with a combining class of zero,
     * which degrades to comparing the string as written: a build with no table
     * still answers, it just cannot see that two spellings are one word.
     */
    @InternalKotlinxLocaleApi
    public fun install(table: String) {
        if (decompositions.isNotEmpty() || table.isEmpty()) return
        val sections = table.split(';')
        if (sections.size != 3) return

        val classes = HashMap<Int, Int>()
        for (entry in sections[0].split(',')) {
            if (entry.isEmpty()) continue
            val colon = entry.indexOf(':')
            classes[entry.substring(0, colon).toInt(36)] = entry.substring(colon + 1).toInt(36)
        }

        val mappings = HashMap<Int, IntArray>()
        for (entry in sections[1].split(',')) {
            if (entry.isEmpty()) continue
            val colon = entry.indexOf(':')
            val target = entry.substring(colon + 1).split('.')
            mappings[entry.substring(0, colon).toInt(36)] = IntArray(target.size) { target[it].toInt(36) }
        }

        val pairs = HashMap<Long, Int>()
        for (entry in sections[2].split(',')) {
            if (entry.isEmpty()) continue
            val colon = entry.indexOf(':')
            val dot = entry.indexOf('.')
            val first = entry.substring(0, dot).toInt(36).toLong()
            val second = entry.substring(dot + 1, colon).toInt(36).toLong()
            pairs[first shl 21 or second] = entry.substring(colon + 1).toInt(36)
        }

        combiningClasses = classes
        decompositions = mappings
        compositions = pairs
    }

    /** The canonical combining class, zero for a starter. */
    @InternalKotlinxLocaleApi
    public fun combiningClass(codePoint: Int): Int = combiningClasses[codePoint] ?: 0

    /**
     * The canonical decomposition, NFD.
     *
     * Decomposes each code point, then puts any run of combining marks into
     * canonical order. The ordering is a stable sort by combining class, which
     * matters: marks of equal class are already in the order the text carries
     * them, and reordering those would change the string rather than normalise
     * it.
     */
    @InternalKotlinxLocaleApi
    public fun decompose(text: String): IntArray {
        val out = ArrayList<Int>(text.length + 8)
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAtIndex(index)
            index += if (codePoint > 0xFFFF) 2 else 1
            appendDecomposed(codePoint, out)
        }
        canonicalOrder(out)
        return out.toIntArray()
    }

    private fun appendDecomposed(codePoint: Int, out: MutableList<Int>) {
        val syllable = codePoint - HANGUL_S_BASE
        if (syllable in 0 until HANGUL_S_COUNT) {
            out.add(HANGUL_L_BASE + syllable / HANGUL_N_COUNT)
            out.add(HANGUL_V_BASE + syllable % HANGUL_N_COUNT / HANGUL_T_COUNT)
            val trailing = syllable % HANGUL_T_COUNT
            if (trailing != 0) out.add(HANGUL_T_BASE + trailing)
            return
        }
        val mapping = decompositions[codePoint]
        if (mapping == null) out.add(codePoint) else for (part in mapping) out.add(part)
    }

    /** Sorts each run of combining marks by combining class, stably, in place. */
    private fun canonicalOrder(out: MutableList<Int>) {
        if (out.size < 2) return
        var index = 1
        while (index < out.size) {
            val current = combiningClass(out[index])
            if (current != 0) {
                var back = index
                while (back > 0) {
                    val previous = combiningClass(out[back - 1])
                    if (previous == 0 || previous <= current) break
                    val swap = out[back]
                    out[back] = out[back - 1]
                    out[back - 1] = swap
                    back--
                }
            }
            index++
        }
    }

    /**
     * The canonical composition, NFC, over an already decomposed sequence.
     *
     * The pairwise algorithm of UAX #15 section 3.11: walk forward from the last
     * starter, and compose it with a following mark only when nothing between
     * them blocks it. A mark blocks when its combining class is not lower than
     * the one before it, which is what keeps `q + ogonek + acute` from composing
     * across the ogonek.
     */
    @InternalKotlinxLocaleApi
    public fun compose(decomposed: IntArray): IntArray {
        if (decomposed.isEmpty()) return decomposed
        val out = ArrayList<Int>(decomposed.size)
        var starter = decomposed[0]
        var lastClass = -1
        var starterIndex = 0
        out.add(starter)

        for (index in 1 until decomposed.size) {
            val codePoint = decomposed[index]
            val currentClass = combiningClass(codePoint)
            val composed = if (lastClass < currentClass || lastClass == -1) composePair(starter, codePoint) else null
            if (composed != null) {
                out[starterIndex] = composed
                starter = composed
                continue
            }
            if (currentClass == 0) {
                starterIndex = out.size
                starter = codePoint
                // The new starter is immediately before whatever comes next, so
                // nothing sits between them to block a composition. Carrying the
                // previous class forward instead is what stopped a Hangul
                // syllable rebuilding from its jamo, since L and V are both
                // starters and the second would read as blocked by the first.
                lastClass = -1
            } else {
                lastClass = currentClass
            }
            out.add(codePoint)
        }
        return out.toIntArray()
    }

    private fun composePair(first: Int, second: Int): Int? {
        val leading = first - HANGUL_L_BASE
        if (leading in 0 until HANGUL_L_COUNT) {
            val vowel = second - HANGUL_V_BASE
            if (vowel in 0 until HANGUL_V_COUNT) {
                return HANGUL_S_BASE + (leading * HANGUL_V_COUNT + vowel) * HANGUL_T_COUNT
            }
        }
        val syllable = first - HANGUL_S_BASE
        if (syllable in 0 until HANGUL_S_COUNT && syllable % HANGUL_T_COUNT == 0) {
            val trailing = second - HANGUL_T_BASE
            if (trailing in 1 until HANGUL_T_COUNT) return first + trailing
        }
        return compositions[first.toLong() shl 21 or second.toLong()]
    }
}

/** The code point at [index], which is a surrogate pair where the text carries one. */
private fun String.codePointAtIndex(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) {
            return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
        }
    }
    return high.code
}
