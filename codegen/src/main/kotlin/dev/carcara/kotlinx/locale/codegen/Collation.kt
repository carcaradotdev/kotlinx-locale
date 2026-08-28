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
    /**
     * Which reordering group each primary top byte belongs to, named for the
     * scripts that share it.
     *
     * `[reorder Grek]` does not move the Greek letters on their own. UTS #35
     * reorders *groups*, and FractionalUCA writes the grouping down in its
     * `[top_byte]` lines: byte 61 carries `Grek` and `Copt` together, so the two
     * move as one and no rule can separate them. That is the same granularity
     * ICU reorders at, which is why it reorders by lead byte.
     */
    val groupOfTopByte: Map<Int, String> = emptyMap(),
    /**
     * The group a script code names, from the `[reorderingTokens]` lines.
     *
     * A script can span many bytes: `Latn` holds 2A..5E and `Hani` is split
     * either side of the other scripts. It still names one group.
     */
    val groupOfScript: Map<String, String> = emptyMap(),
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
    val groupOfTopByte = LinkedHashMap<Int, String>()
    val groupOfScript = LinkedHashMap<String, String>()
    val scriptBytes = LinkedHashMap<String, MutableList<Int>>()

    file.forEachLine { raw ->
        when {
            // `[top_byte 61 Grek Copt ]`: the names on one byte are one group, and
            // the name is their join so two bytes carrying the same scripts are
            // recognised as the same group.
            raw.startsWith("[top_byte") -> {
                val parts = raw.removePrefix("[top_byte").removeSuffix("]").trim()
                    .substringBefore('#').trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    groupOfTopByte[parts[0].toInt(16)] = parts.drop(1).joinToString("+")
                }
            }
            // `[reorderingTokens Arab 67=1130 ]`: which bytes a script code names.
            raw.startsWith("[reorderingTokens") -> {
                val parts = raw.removePrefix("[reorderingTokens").removeSuffix("]").trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val bytes = scriptBytes.getOrPut(parts[0]) { ArrayList() }
                    for (pair in parts.drop(1)) {
                        val equals = pair.indexOf('=')
                        if (equals > 0) bytes.add(pair.substring(0, equals).toInt(16))
                    }
                }
            }
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

    // A script names whichever group its bytes belong to. Every byte a script
    // claims carries the same group name, because the group is what the byte is
    // labelled with, so the first one answers for all of them.
    for ((script, bytes) in scriptBytes) {
        val group = bytes.firstNotNullOfOrNull { groupOfTopByte[it] } ?: continue
        groupOfScript[script] = group
    }

    return CollationRoot(withHan, prefixed, hanOrder, groupOfTopByte, groupOfScript)
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
        /**
         * Eight thousand, which is what a starred list needs.
         *
         * A chain mints one weight per link and each link steps by one, so the
         * gap is the number of letters a rule may insert between two root
         * weights. Japanese writes `<*` lists of several thousand kana and Tamil
         * is not far behind, and a thousand is not enough for either.
         *
         * The ceiling is Int. The primary level carries about 138,000 distinct
         * weights, so the last rank is roughly 1.13 billion here, against a limit
         * of 2.15 billion. Sixteen thousand would not fit. It costs nothing to
         * write down: both 141 million and 1.13 billion are six base-36 digits.
         */
        const val BASE_GAP: Int = 15000

        /**
         * The secondary and tertiary gap, which can afford to be far wider than
         * the primary one.
         *
         * Those two levels carry 268 and 30 distinct weights against the primary
         * level's 138,000, so the same Int ceiling buys a gap four thousand times
         * larger. It is needed: an `[import]` brings a whole second locale's
         * chains into the same gaps, and the accent and case levels are where
         * chains like `&D<dž<<<ǆ<<<Dž<<<ǅ<<<DŽ<<<Ǆ` mint repeatedly from one
         * anchor. At 268 weights the last secondary rank is 1.12 billion, inside
         * the 2.15 billion Int allows.
         */
        const val LEVEL_GAP: Int = 1 shl 22

        /**
         * The tertiary gap, wider again because that level has fewest weights.
         *
         * Thirty distinct tertiaries against the secondary level's 268, so the
         * same Int ceiling buys sixteen times the room: the last tertiary rank is
         * 2.01 billion against the 2.15 billion Int allows. The room is needed
         * because the case level is where the longest chains land. Croatian's
         * `&D<dž<<<ǆ<<<Dž<<<ǅ<<<DŽ<<<Ǆ` is six links into one gap, and an
         * `[import]` brings a second locale's chains into the same gaps again.
         */
        const val TERTIARY_GAP: Int = 1 shl 26

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
            fun ranked(values: Collection<List<Int>>, gap: Int) = values.withIndex().associate { (i, v) -> v to (i + 1) * gap }
            return WeightRanks(
                primaryRanks,
                ranked(secondaries, LEVEL_GAP),
                ranked(tertiaries, TERTIARY_GAP),
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

/**
 * The groups that sit before the scripts and that no CLDR locale reorders.
 *
 * UTS #35 allows `[reorder digit]` and the rest, and nothing in CLDR 48 writes
 * one. Holding them still means the reorderable region starts at the first
 * script, which is what keeps punctuation and digits above every alphabet
 * whichever alphabet a locale lifts.
 */
private val FIXED_GROUPS = setOf("TERMINATOR", "LEVEL-SEPARATOR", "FIELD-SEPARATOR")

private val SPECIAL_GROUP_NAMES = setOf("SPACE", "PUNCTUATION", "SYMBOL", "CURRENCY", "DIGIT")

/** One contiguous run of primary ranks owned by one reordering group. */
class GroupRange(val group: String, val from: Int, val to: Int)

/**
 * The primary rank space cut into reordering groups, in root order.
 *
 * Ranks climb with the weights they were made from, and a group is a run of top
 * bytes, so a group owns whole runs of ranks. Han owns two of them, one either
 * side of the other scripts, and both have to move when it is reordered.
 *
 * The implicit band is left out: it is above every listed weight by
 * construction, and moving a script into it would put unlisted characters in
 * the middle of an alphabet.
 */
fun groupRanges(root: CollationRoot, ranks: WeightRanks): List<GroupRange> {
    val byRank = ranks.primary.entries
        .filter { it.value < ranks.implicitBase }
        .mapNotNull { (weight, rank) ->
            val group = root.groupOfTopByte[weight.first()] ?: return@mapNotNull null
            rank to group
        }
        .sortedBy { it.first }
    val ranges = ArrayList<GroupRange>()
    var index = 0
    while (index < byRank.size) {
        val group = byRank[index].second
        val from = byRank[index].first
        var last = from
        while (index < byRank.size && byRank[index].second == group) {
            last = byRank[index].first
            index++
        }
        ranges.add(GroupRange(group, from, last))
    }
    return ranges
}

/**
 * Where each primary rank moves when [wanted] groups are lifted to the front.
 *
 * Returned as `(from, to, newFrom)` triples over the old rank space, which is
 * all a reader needs: a rank inside a triple shifts by a constant, and one
 * outside every triple does not move. That is a few dozen numbers rather than a
 * second copy of the table.
 *
 * The named groups come first in the order the rule gives them, then everything
 * else in root order. Runs of the same group stay together, so reordering Han
 * moves both of its halves as one block.
 */
fun reorderPlan(ranges: List<GroupRange>, wanted: List<String>): List<IntArray> {
    val movable = ranges.filter { it.group !in FIXED_GROUPS && it.group.split('+').none { name -> name in SPECIAL_GROUP_NAMES } }
    if (movable.isEmpty()) return emptyList()
    val blocks = LinkedHashMap<String, MutableList<GroupRange>>()
    for (range in movable) blocks.getOrPut(range.group) { ArrayList() }.add(range)

    val order = ArrayList<String>(blocks.size)
    for (group in wanted) if (group in blocks && group !in order) order.add(group)
    for (group in blocks.keys) if (group !in order) order.add(group)

    val start = movable.minOf(GroupRange::from)
    val plan = ArrayList<IntArray>()
    var cursor = start
    for (group in order) {
        for (range in blocks.getValue(group)) {
            plan.add(intArrayOf(range.from, range.to, cursor))
            cursor += range.to - range.from + WeightRanks.BASE_GAP
        }
    }
    // A run that did not move carries no information, and the runtime pays for
    // every triple it has to search.
    return plan.filter { it[0] != it[2] }.sortedBy { it[0] }
}

/** The reorder plan as the delta section `PayloadCollation.tailored` reads. */
fun encodeReorderPlan(plan: List<IntArray>): String = plan.joinToString(",") { base36(it[0]) + "." + base36(it[1]) + "." + base36(it[2]) }

private fun base36(value: Int): String = value.toString(36)

private fun encodeElements(ranked: List<IntArray>): String {
    val levels = Array(3) { level -> ranked.mapNotNull { if (it[level] != 0) base36(it[level]) else null } }
    return levels.joinToString("/") { it.joinToString(".") }
}

/**
 * One span of the radical-stroke order that is contiguous in both code point and
 * rank, so the runtime can compute every weight in it from the first.
 *
 * The Han ideographs are 101,996 of the root table's 149,669 entries and none of
 * them carries anything but a primary: the secondary and tertiary are the
 * defaults, and the primary is a function of the character's position in the
 * radical-stroke order. Writing them out one per entry costs 2.19 million
 * characters to say what these spans say in a hundred and forty thousand.
 *
 * [firstRank] rather than the index, because the runtime compares ranks and
 * would otherwise have to know how [WeightRanks] assigns them. A span ends
 * wherever either sequence breaks, which is what makes the arithmetic exact
 * rather than nearly right: the radical-stroke order is not code point order,
 * and a thousand non-Han weights sort inside the same band.
 */
class HanSpan(val startCodePoint: Int, val length: Int, val firstRank: Int)

/**
 * The Han entries as spans, in code point order so a reader can binary search.
 *
 * Built over the radical-stroke order, because that is where the two sequences
 * advance together; sorted afterwards, which cannot break a span.
 */
fun hanSpans(root: CollationRoot, ranks: WeightRanks): List<HanSpan> {
    val rankOf = LinkedHashMap<Int, Int>(root.hanOrder.size)
    for (codePoint in root.hanOrder) {
        val element = root.entries[listOf(codePoint)]?.singleOrNull() ?: continue
        if (element.primary.isEmpty()) continue
        rankOf[codePoint] = ranks.primary.getValue(element.primary)
    }
    val order = root.hanOrder.filter { it in rankOf }
    val spans = ArrayList<HanSpan>()
    var i = 0
    while (i < order.size) {
        val startCodePoint = order[i]
        val firstRank = rankOf.getValue(startCodePoint)
        var length = 1
        while (i + length < order.size &&
            order[i + length] == startCodePoint + length &&
            rankOf.getValue(order[i + length]) == firstRank + length * WeightRanks.BASE_GAP
        ) {
            length++
        }
        spans.add(HanSpan(startCodePoint, length, firstRank))
        i += length
    }
    return spans.sortedBy(HanSpan::startCodePoint)
}

/**
 * The root table in the form `PayloadCollation.install` reads.
 *
 * Five sections. The header carries the constants a reader cannot derive, then
 * the single characters, the contractions, the prefix rules and the Han spans.
 * Han is last and separate because it is the only part that is arithmetic rather
 * than data; see [HanSpan].
 */
fun encodeCollationRoot(root: CollationRoot, ranks: WeightRanks): String {
    val spans = hanSpans(root, ranks)
    val han = HashSet<Int>(root.hanOrder.size)
    for (span in spans) for (offset in 0 until span.length) han.add(span.startCodePoint + offset)

    val singles = ArrayList<String>()
    val contractions = ArrayList<String>()
    for ((key, elements) in root.entries) {
        if (key.size == 1 && key[0] in han) continue
        val encoded = encodeElements(elements.map { ranks.rank(it) })
        if (key.size == 1) {
            singles.add(base36(key[0]) + ":" + encoded)
        } else {
            contractions.add(key.joinToString(".") { base36(it) } + ":" + encoded)
        }
    }
    val prefixes = root.prefixed.map { (pair, elements) ->
        base36(pair.first) + "." + base36(pair.second) + ":" + encodeElements(elements.map { ranks.rank(it) })
    }
    // Deltas, because the spans are sorted and both columns climb. The first
    // code point of a span is within a few of the last one's end, and the ranks
    // are multiples of the gap, so the differences are short where the values
    // are not.
    var previousCodePoint = 0
    var previousRank = 0
    val hanEncoded = spans.joinToString(",") { span ->
        val entry = base36(span.startCodePoint - previousCodePoint) + "." +
            base36(span.length) + "." +
            base36(span.firstRank - previousRank)
        previousCodePoint = span.startCodePoint
        previousRank = span.firstRank
        entry
    }
    val header = listOf(
        base36(ranks.implicitBase),
        base36(ranks.defaultSecondary()),
        base36(ranks.defaultTertiary()),
        base36(WeightRanks.BASE_GAP),
    ).joinToString(",")
    return listOf(header, singles.joinToString(","), contractions.joinToString(","), prefixes.joinToString(","), hanEncoded)
        .joinToString(";")
}

/**
 * The rules `[import <id>]` names, or null when this checkout has no such type.
 *
 * The id is a BCP 47 locale with the collation type in a `-u-co-` extension, so
 * `de-u-co-phonebk` is the phone book ordering of the German file and a bare
 * `es` is the standard ordering of the Spanish one. `und` is root.
 *
 * Null rather than an error for the private types: `ja-u-co-private-kana` and
 * `zh-u-co-private-pinyin` name orderings CLDR builds internally and does not
 * publish, so a checkout genuinely does not have them and importing nothing is
 * the honest answer.
 */
fun importedRules(collationDir: File, id: String): String? {
    val requested = if ("-u-co-" in id) id.substringAfter("-u-co-") else "standard"
    // `de-u-co-phonebk` names the ordering the German file calls `phonebook`.
    // The BCP 47 key is capped at eight characters and the LDML type is not, so
    // the two spellings differ for four of them and CLDR writes the mapping down
    // rather than leaving it to be guessed.
    val type = collationTypeAliases(collationDir)[requested] ?: requested
    val locale = id.substringBefore("-u-co-").ifEmpty { "root" }
    val file = collationDir.resolve("${if (locale == "und") "root" else locale.replace('-', '_')}.xml")
    if (!file.isFile) return null
    return collationRules(file, type).ifEmpty { null }
}

/**
 * The BCP 47 collation key to LDML collation type map, from CLDR's own registry.
 *
 * Cached, because an import resolves it once per rule and there are a hundred and
 * twelve tailorings.
 */
private val collationTypeAliasCache = HashMap<String, Map<String, String>>()

private fun collationTypeAliases(collationDir: File): Map<String, String> = collationTypeAliasCache.getOrPut(collationDir.path) {
    val file = collationDir.resolveSibling("bcp47").resolve("collation.xml")
    if (!file.isFile) {
        emptyMap()
    } else {
        Regex("""<type name="([a-z0-9]+)"[^>]*?alias="([a-z0-9-]+)""")
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }
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
