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

/**
 * The canonical decomposition data UAX #15 normalisation is written in terms of.
 *
 * Collation reads a string as a sequence of collation elements, and UTS #10
 * states that mapping over the decomposed form: `Ä` has to weigh the same
 * whether it arrived as one code point or as `A` plus a combining diaeresis.
 * Without normalisation the two spell the same word and sort to different
 * places, which is a difference no reader can see and every reader would call a
 * bug.
 *
 * The data is `UnicodeData.txt` field 3 (the canonical combining class) and
 * field 5 (the decomposition mapping, taken only where it carries no
 * `<compatibility>` tag), plus `CompositionExclusions.txt`. Hangul is not in any
 * of them: UAX #15 composes and decomposes the syllables arithmetically, so the
 * runtime does too and the table stays out of it.
 *
 * The recursion and the exclusion set are resolved here rather than at runtime.
 * A full decomposition is what NFD emits, so emitting it directly means the
 * runtime walks a map rather than a graph, and the composition pairs are the
 * primary decompositions minus the three kinds of exclusion, which is a
 * derivation with a specification to check against rather than a loop to get
 * right twice.
 */
class NormalizationData(
    /** Canonical combining class per code point, non-zero only. */
    val combiningClasses: Map<Int, Int>,
    /** Full canonical decomposition per code point, recursively applied. */
    val decompositions: Map<Int, List<Int>>,
    /** Composable starter and combining pairs, keyed as `first shl 21 or second`. */
    val compositions: Map<Long, Int>,
)

/** Reads `UnicodeData.txt`, whose fields are positional and semicolon separated. */
private fun unicodeDataLines(): List<List<String>> {
    val text = checkNotNull(object {}.javaClass.getResourceAsStream("/ucd/UnicodeData.txt")) {
        "vendored UCD file /ucd/UnicodeData.txt is missing"
    }.bufferedReader().readText()

    // UnicodeData.txt is the one vendored file that states no version: it opens
    // on its first record rather than on a header. It is checked by content
    // instead, the way emoji-data.txt is, on a decomposition this file exists to
    // provide and a release older than the pin does not carry.
    check(text.contains("\n0041;LATIN CAPITAL LETTER A;")) { "UnicodeData.txt carries no Latin block" }

    return text.lineSequence()
        .filter(String::isNotEmpty)
        .map { line -> line.split(';') }
        .toList()
}

/** Reads the script-specific composition exclusions, which do carry a version header. */
private fun compositionExclusions(): Set<Int> {
    val text = checkNotNull(object {}.javaClass.getResourceAsStream("/ucd/CompositionExclusions.txt")) {
        "vendored UCD file /ucd/CompositionExclusions.txt is missing"
    }.bufferedReader().readText()

    val declared = Regex("""^# \S+-(\d+\.\d+\.\d+)\.txt""").find(text)?.groupValues?.get(1)
    check(declared == UCA_UCD_VERSION) {
        "CompositionExclusions.txt declares Unicode $declared, expected $UCA_UCD_VERSION"
    }

    return text.lineSequence()
        .map { it.substringBefore('#').trim() }
        .filter(String::isNotEmpty)
        .map { it.toInt(16) }
        .toSet()
}

/**
 * Builds the tables, resolving the recursion and the three kinds of exclusion.
 *
 * UAX #15 excludes a composite from NFC when it is listed in
 * `CompositionExclusions.txt`, when its decomposition is a single code point,
 * or when its decomposition starts with a non-starter. The last two are derived
 * rather than listed, which is why they are computed here from the same pass
 * that reads the mappings.
 */
fun parseNormalizationData(): NormalizationData {
    val combining = LinkedHashMap<Int, Int>()
    val primary = LinkedHashMap<Int, List<Int>>()

    for (fields in unicodeDataLines()) {
        val codePoint = fields[0].toInt(16)

        val ccc = fields[3].toInt()
        if (ccc != 0) combining[codePoint] = ccc

        val mapping = fields[5]
        // A compatibility mapping is tagged, and NFD does not apply it.
        if (mapping.isEmpty() || mapping.startsWith('<')) continue
        primary[codePoint] = mapping.trim().split(' ').map { it.toInt(16) }
    }

    val full = LinkedHashMap<Int, List<Int>>()
    fun decompose(codePoint: Int): List<Int> {
        full[codePoint]?.let { return it }
        val mapping = primary[codePoint] ?: return listOf(codePoint)
        val expanded = mapping.flatMap(::decompose)
        full[codePoint] = expanded
        return expanded
    }
    for (codePoint in primary.keys) decompose(codePoint)

    val excluded = compositionExclusions()
    val compositions = LinkedHashMap<Long, Int>()
    for ((composite, mapping) in primary) {
        if (composite in excluded) continue
        // A singleton decomposition composes back from one code point, which
        // would make composition ambiguous, so NFC never rebuilds it.
        if (mapping.size != 2) continue
        // A decomposition whose first code point is itself a combining mark
        // cannot be rebuilt by the pairwise algorithm, which only ever looks
        // back to the last starter.
        if (combining.getOrDefault(mapping[0], 0) != 0) continue
        compositions[mapping[0].toLong() shl 21 or mapping[1].toLong()] = composite
    }

    return NormalizationData(combining, full, compositions)
}

/**
 * Encodes the tables as three base-36 sections, in the order the runtime reads
 * them.
 *
 * The same shape the other property tables use: base 36 because it is the
 * widest radix `toString` and `toInt` both take without a table of their own,
 * and one string because a bundle section carries one.
 */
fun encodeNormalizationData(data: NormalizationData): String {
    val classes = data.combiningClasses.entries.joinToString(",") { (codePoint, ccc) ->
        codePoint.toString(36) + ":" + ccc.toString(36)
    }
    val decompositions = data.decompositions.entries.joinToString(",") { (codePoint, mapping) ->
        codePoint.toString(36) + ":" + mapping.joinToString(".") { it.toString(36) }
    }
    val compositions = data.compositions.entries.joinToString(",") { (pair, composite) ->
        (pair ushr 21).toString(36) + "." + (pair and 0x1FFFFF).toString(36) + ":" + composite.toString(36)
    }
    return listOf(classes, decompositions, compositions).joinToString(";")
}

/**
 * The canonical decomposition of a code point sequence, NFD.
 *
 * Implemented here rather than taken from `java.text.Normalizer` so the
 * generator and the runtime agree by construction: both read the same vendored
 * mappings and both order the marks the same way.
 */
fun NormalizationData.decompose(points: List<Int>): List<Int> {
    val out = ArrayList<Int>(points.size + 4)
    for (point in points) {
        val syllable = point - 0xAC00
        if (syllable in 0 until 19 * 21 * 28) {
            out.add(0x1100 + syllable / (21 * 28))
            out.add(0x1161 + syllable % (21 * 28) / 28)
            val trailing = syllable % 28
            if (trailing != 0) out.add(0x11A7 + trailing)
        } else {
            val mapping = decompositions[point]
            if (mapping == null) out.add(point) else out.addAll(mapping)
        }
    }
    var i = 1
    while (i < out.size) {
        val current = combiningClasses[out[i]] ?: 0
        if (current != 0) {
            var back = i
            while (back > 0) {
                val previous = combiningClasses[out[back - 1]] ?: 0
                if (previous == 0 || previous <= current) break
                val swap = out[back]
                out[back] = out[back - 1]
                out[back - 1] = swap
                back--
            }
        }
        i++
    }
    return out
}
