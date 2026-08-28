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

/**
 * The items of a starred rule list, one string each.
 *
 * Characters stand for themselves, `a-z` is the range between two of them, and a
 * quoted run is literal so that a locale can list a hyphen. Ranges count in code
 * points rather than in chars: Japanese lists its era signs that way and they
 * are all above the basic plane.
 */
internal fun expandRuleList(source: String): List<String> {
    val items = ArrayList<String>()
    var index = 0
    while (index < source.length) {
        if (source[index] == '\'') {
            index++
            val literal = StringBuilder()
            while (index < source.length && source[index] != '\'') literal.append(source[index++])
            index++
            if (literal.isNotEmpty()) items.add(literal.toString())
            continue
        }
        val point = source.codePointAt(index)
        index += Character.charCount(point)
        if (index < source.length && source[index] == '-' && index + 1 < source.length) {
            val last = source.codePointAt(index + 1)
            index += 1 + Character.charCount(last)
            for (cp in point..last) items.add(String(Character.toChars(cp)))
        } else {
            items.add(String(Character.toChars(point)))
        }
    }
    return items
}

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
                i += op.length
                // `&x <* abcd` is `&x < a < b < c < d`, and `<* a-d` is the same
                // written as a range. Japanese, Korean, Persian, Pashto and
                // Chinese all use it, and reading the list as one long piece of
                // text places a word nobody wrote instead of four letters.
                if (i < text.length && text[i] == '*') {
                    i++
                    while (i < text.length && text[i].isWhitespace()) i++
                    val start = i
                    while (i < text.length && !text[i].isWhitespace() && text[i] !in "<=&[") i++
                    for (item in expandRuleList(text.substring(start, i))) {
                        tokens.add(RuleToken(KIND_OP, op, null, null))
                        tokens.add(RuleToken(KIND_TEXT, item, null, null))
                    }
                } else {
                    tokens.add(RuleToken(KIND_OP, op, null, null))
                }
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
class TailoredTable(
    private val root: CollationRoot,
    private val ranks: WeightRanks,
    private val normalization: NormalizationData,
    /**
     * How finely a chain divides the gap it is minting into: the step is a
     * [spread]th of what is left, never less than one.
     *
     * There is no value that suits every locale, which is why it is a parameter
     * rather than a constant. A large spread steps by one and gives a chain the
     * whole gap, which is what Japanese needs for its thousands of kana, but it
     * leaves nothing between two links for a later rule to sit in. A small
     * spread leaves that room, which is what Tamil needs, and runs out sooner.
     *
     * [tailoringFor] tries them in turn. Both orders are correct; they differ
     * only in where they put the numbers.
     */
    private val spread: Int = SPREAD_ROOMY,
) {
    private val entries = LinkedHashMap<List<Int>, List<IntArray>>()
    private val prefixed = LinkedHashMap<Pair<Int, Int>, List<IntArray>>()
    private val addedEntries = LinkedHashMap<List<Int>, List<IntArray>>()
    private val addedPrefixes = LinkedHashMap<Pair<Int, Int>, List<IntArray>>()
    private val used = Array(3) { sortedSetOf<Int>() }
    private var longest = 1

    /**
     * Where the reordered groups move to, as `(from, to, newFrom)` over primary
     * ranks. Empty until a `[reorder ...]` directive says otherwise.
     */
    private var reorder: List<IntArray> = emptyList()

    /**
     * Tertiary ranks that exchange, for `[caseFirst upper]`.
     *
     * Always empty today, and the section it writes is always empty with it.
     * A tertiary weight is a class shared by many characters rather than a
     * per-letter value, and deriving which class is the capital of which from
     * the table gives the wrong answer for the ordinary Latin letters: the
     * pairing that occurs most often across the whole table is not the one
     * between `a` and `A`. Shipping a swap that is nearly right would reorder
     * every cased letter in Danish and Maltese by a rule nobody wrote, so the
     * directive is read and left alone until the class structure is understood.
     * `conformance/ledger/collation-order.tsv` records the three locales.
     */
    private var caseSwap: List<IntArray> = emptyList()

    /**
     * Whether the secondary level is read back to front, for `[backwards 2]`.
     *
     * Canadian French orders words by their last accent rather than their first,
     * so `côté` sorts before `coté`. It is the one level-ordering rule in CLDR
     * and it applies to the whole locale.
     */
    private var backwardsSecondary = false

    /** Contractions the locale asks to be ignored, by their first code point. */
    private val suppressed = HashSet<Int>()

    /**
     * A primary rank after reordering.
     *
     * Applied when the sort key is built rather than when the table is loaded,
     * because the rules that follow the directive anchor on root weights and
     * have to keep doing so. Reordering is the last thing that happens to a
     * primary, which is also how ICU does it: it permutes lead bytes after the
     * tailoring is folded in.
     */
    private fun reordered(primary: Int): Int {
        for (range in reorder) if (primary >= range[0] && primary <= range[1]) return primary - range[0] + range[2]
        return primary
    }

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
            // A suppressed first character takes its own weight and never starts
            // a contraction. Serbian and Macedonian ask for that so that "Ии"
            // does not swallow the letter after it.
            var take = if (points[i] in suppressed) 1 else minOf(longest, points.size - i)
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
        // The first weight already in use above the anchor, which is the real
        // ceiling whether or not the rule named one. Taking `before` on its own
        // was the bug: a rule asking to sit before X would bisect a span that
        // already had weights in it, land on one of them, and then have to walk,
        // and walking packs the span solid until the next rule has nowhere left.
        // Stopping at the first used value instead leaves the whole interval
        // free by construction, so nothing ever has to walk.
        val used = used[level].tailSet(after + 1).firstOrNull()
        val next = when {
            before != null -> if (used != null && used < before) used else before
            used != null -> used
            else -> after + 2 * WeightRanks.BASE_GAP
        }
        // Always just above the anchor, never the midpoint.
        //
        // The midpoint looks like the fair answer and converges. `[before 3]`
        // rules chain by anchoring each new weight on the one before it while
        // keeping the same ceiling, so bisecting halves the same interval once
        // per rule and closes it after twenty-six. Japanese writes more than
        // twenty-six of them over its kana.
        //
        // Stepping up from the anchor cannot converge, because the interval
        // above is free by construction: `next` is the first weight already in
        // use, so nothing else is claiming the space this walks into.
        //
        // The step also has a floor, and that is what makes `[before]` work. A
        // chain that stepped by one would leave adjacent links with no integer
        // between them, and the next rule asking to sit before one of them would
        // have nowhere to go. A floor of a two-thousandth of the level's own gap
        // leaves two thousand links, each with room inside it for the same again.
        val room = (next.toLong() - after.toLong()).toInt()
        val step = minOf(maxOf(room / spread, stepFloor(level)), maxOf(1, room - 1))
        val value = after + step
        require(value > after && value < next) { "no room at level $level between $after and $next" }
        this.used[level].add(value)
        minted[key] = value
        return value
    }

    /**
     * The smallest step a chain takes at [level], which is what decides how many
     * links fit in a gap and how much room is left inside each link.
     *
     * The two are in tension and the levels want opposite answers. The primary
     * level has the narrowest gap and the longest chains, because Japanese places
     * several thousand kana one after another, so it steps by two and leaves one
     * slot between links. The secondary and tertiary levels have gaps thousands
     * of times wider and shorter chains, and they are where `[before]` rules
     * insert, so they leave far more.
     */
    private fun stepFloor(level: Int): Int = when (level) {
        0 -> 2
        1 -> WeightRanks.LEVEL_GAP / 2048
        else -> WeightRanks.TERTIARY_GAP / 2048
    }

    private fun predecessor(level: Int, value: Int): Int = used[level].headSet(value).lastOrNull() ?: 0

    @JvmOverloads
    fun apply(rules: String, resolveImport: (String) -> String? = { null }) {
        val tokens = tokenizeRules(rules)
        var anchor: List<IntArray>? = null
        var beforeLevel: Int? = null
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when (token.kind) {
                KIND_DIRECTIVE -> {
                    when {
                        token.text.startsWith("before") -> beforeLevel = token.text.split(' ')[1].toInt()
                        token.text.startsWith("reorder") -> {
                            val scripts = token.text.removePrefix("reorder").trim()
                                .split(' ')
                                .filter(String::isNotEmpty)
                            val groups = scripts.mapNotNull { root.groupOfScript[it] }.distinct()
                            if (groups.isNotEmpty()) reorder = reorderPlan(groupRanges(root, ranks), groups)
                        }
                        // Applied where it stands rather than hoisted: an import
                        // is a paste, and the rules after it anchor on what it
                        // brought in.
                        token.text.startsWith("import") -> {
                            val id = token.text.removePrefix("import").trim()
                            val imported = resolveImport(id)
                            if (imported == null) {
                                println("[codegen] collation: no rules for [import $id], skipped")
                            } else {
                                apply(imported, resolveImport)
                            }
                        }
                        token.text.startsWith("backwards") -> backwardsSecondary = token.text.trim().endsWith("2")
                        token.text.startsWith("suppressContractions") -> {
                            for (point in unicodeSetCodePoints(token.text.substringAfter("suppressContractions"))) {
                                suppressed.add(point)
                            }
                        }
                    }
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
        // An anchor that is ignorable at the level being tailored has no weight
        // there to count from, and counting from zero asks for a weight between
        // nothing and nothing. The default is what an unmarked character carries
        // at that level, so it is the weight the rule means: Arabic's
        // `&[before 2]` on a secondary-ignorable anchor is asking to sit before
        // the plain form, not before the absence of one.
        val secondaryAnchor = if (base[1] == 0) ranks.defaultSecondary() else base[1]
        val tertiaryAnchor = if (base[2] == 0) ranks.defaultTertiary() else base[2]
        val minted = when (strength) {
            0 -> intArrayOf(base[0], base[1], base[2])
            1 -> intArrayOf(
                if (beforeLevel == 1) mint(0, predecessor(0, base[0]), base[0]) else mint(0, base[0], null),
                ranks.defaultSecondary(),
                ranks.defaultTertiary(),
            )
            2 -> intArrayOf(
                base[0],
                if (beforeLevel == 2) {
                    mint(1, predecessor(1, secondaryAnchor), secondaryAnchor)
                } else {
                    mint(1, secondaryAnchor, null)
                },
                ranks.defaultTertiary(),
            )
            else -> intArrayOf(
                base[0],
                base[1],
                if (beforeLevel == 3) {
                    mint(2, predecessor(2, tertiaryAnchor), tertiaryAnchor)
                } else {
                    mint(2, tertiaryAnchor, null)
                },
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

    private fun cased(tertiary: Int): Int {
        for (pair in caseSwap) if (pair[0] == tertiary) return pair[1]
        return tertiary
    }

    /** The sort key this table gives a string, for holding it to ICU. */
    fun sortKey(text: String): List<Int> {
        val elements = elementsFor(text)
        val key = ArrayList<Int>(elements.size * 3 + 2)
        for (level in 0..2) {
            val weights = elements.mapNotNull { element ->
                when {
                    element[level] == 0 -> null
                    level == 0 -> reordered(element[level])
                    level == 2 -> cased(element[level])
                    else -> element[level]
                }
            }
            key.addAll(if (level == 1 && backwardsSecondary) weights.reversed() else weights)
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
        return listOf(
            singles.joinToString(","),
            contractions.joinToString(","),
            prefixes.joinToString(","),
            encodeReorderPlan(reorder),
            caseSwap.joinToString(",") { it[0].toString(36) + "." + it[1].toString(36) },
            if (backwardsSecondary) "b" else "",
            suppressed.sorted().joinToString(",") { it.toString(36) },
        ).joinToString(";")
    }
}

/**
 * The code points of a bracketed UnicodeSet, which is all CLDR writes here.
 *
 * `[เ-ไ ເ-ໄ ꪵ]` and `[Ии]`: literal characters, ranges, and `\uXXXX` escapes.
 * The full UnicodeSet grammar has properties and set algebra in it and no CLDR
 * collation file uses any of that, so parsing it would be code no data reaches.
 */
internal fun unicodeSetCodePoints(source: String): Set<Int> {
    val body = source.trim().removePrefix("[").removeSuffix("]")
    val points = LinkedHashSet<Int>()
    var index = 0
    var previous = -1
    while (index < body.length) {
        val ch = body[index]
        if (ch.isWhitespace()) {
            index++
            previous = -1
            continue
        }
        if (ch == '-' && previous >= 0 && index + 1 < body.length) {
            index++
            val (last, next) = readSetPoint(body, index)
            for (point in previous..last) points.add(point)
            index = next
            previous = -1
            continue
        }
        val (point, next) = readSetPoint(body, index)
        points.add(point)
        previous = point
        index = next
    }
    return points
}

private fun readSetPoint(body: String, at: Int): Pair<Int, Int> {
    if (body.startsWith("\\u", at)) {
        return body.substring(at + 2, at + 6).toInt(16) to at + 6
    }
    val point = body.codePointAt(at)
    return point to at + Character.charCount(point)
}

/** Leaves room between the links of a chain, and runs out of gap sooner. */
const val SPREAD_ROOMY: Int = 1024

/** Steps by one: the whole gap for one chain, and nothing between its links. */
const val SPREAD_LONG: Int = Int.MAX_VALUE

/**
 * One locale's table, built with whichever spread its rules fit in.
 *
 * Every spread produces the same order. They differ in how the minted weights
 * are spaced, and a locale's rules decide which spacing has room: Japanese needs
 * the long one and Tamil needs the roomy one. Trying them in turn is cheaper
 * than a weight allocator that cannot run out, and the result is identical
 * wherever both succeed.
 *
 * Throws when no spread fits, because a tailoring that cannot be built is a
 * locale that would silently sort in root order.
 */
fun tailoringFor(
    root: CollationRoot,
    ranks: WeightRanks,
    normalization: NormalizationData,
    rules: String,
    resolveImport: (String) -> String? = { null },
): TailoredTable {
    var failure: Exception? = null
    for (spread in intArrayOf(SPREAD_ROOMY, SPREAD_LONG)) {
        val table = TailoredTable(root, ranks, normalization, spread)
        try {
            table.apply(rules, resolveImport)
            return table
        } catch (e: IllegalArgumentException) {
            failure = e
        }
    }
    throw IllegalStateException("no weight spread fits these rules", failure)
}

internal fun encodeRankedElements(elements: List<IntArray>): String {
    val levels = Array(3) { level -> elements.mapNotNull { if (it[level] != 0) it[level].toString(36) else null } }
    return levels.joinToString("/") { it.joinToString(".") }
}
