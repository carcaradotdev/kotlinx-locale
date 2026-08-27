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
 * A locale's collation rules, applied over the ranked root.
 *
 * The rules are the syntax UTS #35 Part 5 defines and CLDR writes: `&C<č` places
 * č after C at primary strength, `<<` and `<<<` do the same at secondary and
 * tertiary, `[before 1]` anchors ahead of a letter rather than behind it, `/`
 * carries an expansion and `|` a prefix. Applying one means minting a weight
 * between the anchor and whatever currently follows it, which the gaps the
 * ranking leaves are there for.
 */
class RuleToken(val kind: String, val text: String, val extension: String?, val prefix: String?)

private const val KIND_RESET = "reset"
private const val KIND_OP = "op"
private const val KIND_TEXT = "text"
private const val KIND_DIRECTIVE = "directive"

/** Rule text to tokens. Comments, quoting, expansions and prefixes are handled here. */
fun tokenizeRules(rules: String): List<RuleToken> {
    val text = rules.lines().joinToString("\n") { it.substringBefore('#') }
    val tokens = ArrayList<RuleToken>()
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            ch.isWhitespace() -> i++
            ch == '[' -> {
                var depth = 1
                var j = i + 1
                while (j < text.length && depth > 0) {
                    if (text[j] == '[') {
                        depth++
                    } else if (text[j] == ']') {
                        depth--
                    }
                    j++
                }
                tokens.add(RuleToken(KIND_DIRECTIVE, text.substring(i + 1, j - 1).trim(), null, null))
                i = j
            }
            ch == '&' -> {
                tokens.add(RuleToken(KIND_RESET, "", null, null))
                i++
            }
            ch == '<' || ch == '=' -> {
                var op = ch.toString()
                while (ch == '<' && i + op.length < text.length && text[i + op.length] == ch) op += ch
                tokens.add(RuleToken(KIND_OP, op, null, null))
                i += op.length
            }
            else -> {
                val buffer = StringBuilder()
                var extension: String? = null
                var prefix: String? = null
                var j = i
                while (j < text.length && !text[j].isWhitespace() && text[j] !in "<=&[") {
                    when (text[j]) {
                        '\'' -> {
                            j++
                            while (j < text.length && text[j] != '\'') buffer.append(text[j++])
                            j++
                        }
                        '/' -> {
                            j++
                            val target = StringBuilder()
                            while (j < text.length && !text[j].isWhitespace() && text[j] !in "<=&[/|") target.append(text[j++])
                            extension = target.toString()
                        }
                        '|' -> {
                            j++
                            val target = StringBuilder()
                            while (j < text.length && !text[j].isWhitespace() && text[j] !in "<=&[/|") target.append(text[j++])
                            prefix = buffer.toString()
                            buffer.setLength(0)
                            buffer.append(target)
                        }
                        else -> buffer.append(text[j++])
                    }
                }
                if (buffer.isNotEmpty() || extension != null || prefix != null) {
                    tokens.add(RuleToken(KIND_TEXT, buffer.toString(), extension, prefix))
                }
                i = maxOf(j, i + 1)
            }
        }
    }
    return tokens
}

/**
 * The root with one locale's rules folded in, and the delta that produced it.
 *
 * Only the entries a tailoring touches are kept as the delta, because that is
 * what ships: a few dozen rows beside a root table of a hundred and fifty
 * thousand.
 */
class TailoredTable(private val root: CollationRoot, private val ranks: WeightRanks, private val normalization: NormalizationData) {
    private val entries = LinkedHashMap<List<Int>, List<IntArray>>()
    private val prefixed = LinkedHashMap<Pair<Int, Int>, List<IntArray>>()
    private val addedEntries = LinkedHashMap<List<Int>, List<IntArray>>()
    private val addedPrefixes = LinkedHashMap<Pair<Int, Int>, List<IntArray>>()
    private val used = Array(3) { sortedSetOf<Int>() }
    private var longest = 1

    init {
        for ((key, elements) in root.entries) {
            val ranked = elements.map { ranks.rank(it) }
            entries[key] = ranked
            if (key.size > longest) longest = key.size
            for (element in ranked) for (level in 0..2) if (element[level] != 0) used[level].add(element[level])
        }
        for ((pair, elements) in root.prefixed) prefixed[pair] = elements.map { ranks.rank(it) }
    }

    private fun elementsFor(text: String): List<IntArray> {
        val points = normalization.decompose(text.codePoints().toArray().toList()).toMutableList()
        val out = ArrayList<IntArray>()
        var i = 0
        while (i < points.size) {
            if (i > 0) {
                val rule = prefixed[points[i - 1] to points[i]]
                if (rule != null) {
                    out.addAll(rule)
                    i++
                    continue
                }
            }
            var matched: List<IntArray>? = null
            var length = 0
            var take = minOf(longest, points.size - i)
            while (take >= 1) {
                val candidate = entries[points.subList(i, i + take).toList()]
                if (candidate != null) {
                    matched = candidate
                    length = take
                    break
                }
                take--
            }
            if (matched == null) {
                out.add(intArrayOf(ranks.implicitBase + points[i], ranks.defaultSecondary(), ranks.defaultTertiary()))
                i++
                continue
            }
            // A following non-starter joins the contraction when nothing between
            // them blocks it. Without this the generator disagrees with the
            // runtime wherever combining marks meet a contraction, which is most
            // of Vietnamese.
            var resolved: List<IntArray> = matched
            var accumulated = points.subList(i, i + length).toList()
            var j = i + length
            var lastClass = 0
            while (j < points.size) {
                val combining = normalization.combiningClasses[points[j]] ?: 0
                if (combining == 0) break
                if (combining > lastClass) {
                    val extended = accumulated + points[j]
                    val candidate = entries[extended]
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
            out.addAll(resolved)
            i += length
        }
        return out
    }

    private val minted = HashMap<Triple<Int, Int, Int>, Int>()

    /**
     * A weight strictly between [after] and whatever follows it.
     *
     * Memoised by position, which is what keeps a locale with many chains from
     * running out of room. A tertiary only has to be distinct within one primary,
     * so Hungarian's `&C<cs<<<Cs` and `&D<dz<<<Dz` can hold the same tertiary:
     * they differ a level up. Minting a fresh one for each would halve the same
     * gap once per chain and close it after sixteen, which is fewer chains than
     * Hungarian has.
     */
    private fun mint(level: Int, after: Int, before: Int?): Int {
        // Keyed on the anchor, not on the neighbour: "one step after X" has to be
        // the same answer every time it is asked. Keying on the neighbour instead
        // gives a fresh key each round, because the previous mint became the new
        // neighbour, and the gap halves once per chain until it closes.
        val key = Triple(level, after, before ?: -1)
        minted[key]?.let { return it }
        val next = before ?: (used[level].tailSet(after + 1).firstOrNull() ?: (after + 2 * WeightRanks.BASE_GAP))
        val value = ((after.toLong() + next.toLong()) / 2).toInt()
        require(value > after && value < next) { "no room at level $level between $after and $next" }
        used[level].add(value)
        minted[key] = value
        return value
    }

    private fun predecessor(level: Int, value: Int): Int = used[level].headSet(value).lastOrNull() ?: 0

    fun apply(rules: String) {
        val tokens = tokenizeRules(rules)
        var anchor: List<IntArray>? = null
        var beforeLevel: Int? = null
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when (token.kind) {
                KIND_DIRECTIVE -> {
                    if (token.text.startsWith("before")) beforeLevel = token.text.split(' ')[1].toInt()
                    i++
                }
                KIND_RESET -> {
                    i++
                    if (i < tokens.size && tokens[i].kind == KIND_DIRECTIVE && tokens[i].text.startsWith("before")) {
                        beforeLevel = tokens[i].text.split(' ')[1].toInt()
                        i++
                    }
                    if (i < tokens.size && tokens[i].kind == KIND_TEXT) {
                        anchor = elementsFor(tokens[i].text)
                        i++
                    }
                }
                KIND_OP -> {
                    val strength = when (token.text) {
                        "<" -> 1
                        "<<" -> 2
                        "<<<" -> 3
                        "<<<<" -> 4
                        else -> 0
                    }
                    i++
                    if (i < tokens.size && tokens[i].kind == KIND_TEXT) {
                        anchor = place(tokens[i], anchor, strength, beforeLevel)
                        beforeLevel = null
                        i++
                    }
                }
                else -> i++
            }
        }
    }

    private fun place(token: RuleToken, anchor: List<IntArray>?, strength: Int, beforeLevel: Int?): List<IntArray>? {
        if (anchor == null || anchor.isEmpty()) return anchor
        val base = anchor[0]
        val minted = when (strength) {
            0 -> intArrayOf(base[0], base[1], base[2])
            1 -> intArrayOf(
                if (beforeLevel == 1) mint(0, predecessor(0, base[0]), base[0]) else mint(0, base[0], null),
                ranks.defaultSecondary(),
                ranks.defaultTertiary(),
            )
            2 -> intArrayOf(
                base[0],
                if (beforeLevel == 2) mint(1, predecessor(1, base[1]), base[1]) else mint(1, base[1], null),
                ranks.defaultTertiary(),
            )
            else -> intArrayOf(
                base[0],
                base[1],
                if (beforeLevel == 3) mint(2, predecessor(2, base[2]), base[2]) else mint(2, base[2], null),
            )
        }
        var elements = if (strength >= 2) listOf(minted) + anchor.drop(1) else listOf(minted)
        token.extension?.let { elements = elements + elementsFor(it) }

        // Keyed on the decomposed form, because lookup happens after NFD. CLDR
        // writes some tailorings composed and some decomposed: Czech spells
        // c-caron as two code points and Croatian as one, and keying on the text
        // as written silently loses every composed rule.
        val key = normalization.decompose(token.text.codePoints().toArray().toList())
        val prefix = token.prefix
        if (prefix != null) {
            val prefixPoints = normalization.decompose(prefix.codePoints().toArray().toList())
            val pair = prefixPoints.last() to key[0]
            prefixed[pair] = elements
            addedPrefixes[pair] = elements
        } else {
            entries[key] = elements
            addedEntries[key] = elements
            if (key.size > longest) longest = key.size
        }
        return elements
    }

    /** The sort key this table gives a string, for holding it to ICU. */
    fun sortKey(text: String): List<Int> {
        val elements = elementsFor(text)
        val key = ArrayList<Int>(elements.size * 3 + 2)
        for (level in 0..2) {
            for (element in elements) if (element[level] != 0) key.add(element[level])
            if (level < 2) key.add(0)
        }
        return key
    }

    /** The tailoring as the delta `Collation.tailored` overlays on the root. */
    fun encodeDelta(): String {
        val singles = ArrayList<String>()
        val contractions = ArrayList<String>()
        for ((key, elements) in addedEntries) {
            val encoded = encodeRankedElements(elements)
            if (key.size == 1) {
                singles.add(key[0].toString(36) + ":" + encoded)
            } else {
                contractions.add(key.joinToString(".") { it.toString(36) } + ":" + encoded)
            }
        }
        val prefixes = addedPrefixes.map { (pair, elements) ->
            pair.first.toString(36) + "." + pair.second.toString(36) + ":" + encodeRankedElements(elements)
        }
        return listOf(singles.joinToString(","), contractions.joinToString(","), prefixes.joinToString(",")).joinToString(";")
    }
}

internal fun encodeRankedElements(elements: List<IntArray>): String {
    val levels = Array(3) { level -> elements.mapNotNull { if (it[level] != 0) it[level].toString(36) else null } }
    return levels.joinToString("/") { it.joinToString(".") }
}
