package dev.carcara.kotlinx.locale.codegen

/**
 * The character properties UAX #29's grapheme cluster rules are written in terms
 * of.
 *
 * A grapheme cluster is what a reader calls one character, and it is regularly
 * more than one code point: a base plus its combining marks, a Hangul syllable
 * spelled as jamo, a flag as two regional indicators, an emoji joined with ZWJ,
 * and an Indic consonant bound to the next by a virama. Taking the first
 * "letter" of a name means taking the first cluster, and anything that counts
 * code points instead answers with half a letter.
 *
 * The rules are CLDR's `common/segments/root.xml`, which since CLDR 44 is the
 * UCD's own; the properties are the vendored UCD files. Neither is invented
 * here, and the implementation is held to Unicode's own conformance file.
 */
enum class GraphemeBreakClass {
    OTHER,
    CR,
    LF,
    CONTROL,
    EXTEND,
    ZWJ,
    REGIONAL_INDICATOR,
    PREPEND,
    SPACING_MARK,
    HANGUL_L,
    HANGUL_V,
    HANGUL_T,
    HANGUL_LV,
    HANGUL_LVT,

    /**
     * Not a `Grapheme_Cluster_Break` value but a class the rules need all the
     * same: rule GB11 joins across ZWJ only when both sides are pictographic.
     */
    EXTENDED_PICTOGRAPHIC,
}

/**
 * `Indic_Conjunct_Break`, which rule GB9c is written in terms of.
 *
 * This is the property that makes a Devanagari or Bengali conjunct one cluster.
 * Unicode 15.1 adopted it from CLDR, where it had been since CLDR 35; before
 * that the rule did not exist and a conjunct split in the middle.
 */
enum class IndicConjunctBreak { NONE, CONSONANT, LINKER, EXTEND }

/** One code point range and the class it carries. */
class BreakRange(val first: Int, val last: Int, val gcb: GraphemeBreakClass, val incb: IndicConjunctBreak)

private fun classOf(name: String): GraphemeBreakClass? = when (name) {
    "CR" -> GraphemeBreakClass.CR
    "LF" -> GraphemeBreakClass.LF
    "Control" -> GraphemeBreakClass.CONTROL
    "Extend" -> GraphemeBreakClass.EXTEND
    "ZWJ" -> GraphemeBreakClass.ZWJ
    "Regional_Indicator" -> GraphemeBreakClass.REGIONAL_INDICATOR
    "Prepend" -> GraphemeBreakClass.PREPEND
    "SpacingMark" -> GraphemeBreakClass.SPACING_MARK
    "L" -> GraphemeBreakClass.HANGUL_L
    "V" -> GraphemeBreakClass.HANGUL_V
    "T" -> GraphemeBreakClass.HANGUL_T
    "LV" -> GraphemeBreakClass.HANGUL_LV
    "LVT" -> GraphemeBreakClass.HANGUL_LVT
    else -> null
}

/** Reads a UCD-format file: `0300..036F ; Extend # comment`. */
private fun ucdLines(resource: String): List<List<String>> {
    val text = checkNotNull(object {}.javaClass.getResourceAsStream("/ucd/$resource")) {
        "vendored UCD file /ucd/$resource is missing"
    }.bufferedReader().readText()

    // Most UCD files name their release in the first line, and checking it is
    // what stops the properties and the conformance cases drifting apart, since
    // that would fail in a way that looks like an algorithm bug.
    //
    // emoji-data.txt is the exception: it is versioned as an Emoji release
    // rather than a UCD one and states neither in its header. It is checked by
    // content instead, on a code point whose class this file exists to provide.
    val declared = Regex("""^# \S+-(\d+\.\d+\.\d+)\.txt""").find(text)?.groupValues?.get(1)
    if (declared != null) {
        check(declared == UCD_VERSION) { "$resource declares Unicode $declared, expected $UCD_VERSION" }
    } else {
        check(text.contains("Extended_Pictographic")) { "$resource carries no Extended_Pictographic data" }
    }

    return text.lineSequence()
        .map { it.substringBefore('#').trim() }
        .filter(String::isNotEmpty)
        .map { line -> line.split(';').map(String::trim) }
        .toList()
}

/**
 * The code points UAX #29 rules WB6 and WB7 keep inside a word.
 *
 * `MidLetter`, `MidNumLet` and `Single_Quote`, which are the three classes that
 * do not break a word when they stand between two letters. Emitted as the
 * characters themselves rather than as ranges: there are seventeen of them and
 * they do not form ranges, so a table of starts and ends would be longer than
 * the set it encodes.
 *
 * The rest of the word break properties are deliberately not read. Applying them
 * fully needs the dictionaries the scripts without spaces between words require,
 * and this library ships none.
 */
fun parseWordBreakMidLetters(): String {
    val wanted = setOf("MidLetter", "MidNumLet", "Single_Quote")
    val points = sortedSetOf<Int>()
    for (fields in ucdLines("WordBreakProperty.txt")) {
        if (fields.getOrNull(1) !in wanted) continue
        val (first, last) = parseRange(fields[0])
        for (cp in first..last) points.add(cp)
    }
    check(points.isNotEmpty()) { "WordBreakProperty.txt carried no mid-word classes" }
    println("[codegen] word break: ${points.size} mid-word code points from Unicode $UCD_VERSION")
    return points.joinToString("") { it.toChar().toString() }
}

private fun parseRange(field: String): Pair<Int, Int> {
    val first = field.substringBefore("..")
    val last = field.substringAfter("..", first)
    return first.toInt(16) to last.toInt(16)
}

/**
 * Every code point range that carries a grapheme break class, sorted and merged.
 *
 * Ranges that agree in both properties and touch are joined, because the runtime
 * looks them up by binary search and a shorter table is a smaller artifact and a
 * faster search for the same answers.
 */
fun parseGraphemeBreakRanges(): List<BreakRange> {
    val gcb = HashMap<Int, GraphemeBreakClass>()
    for (fields in ucdLines("GraphemeBreakProperty.txt")) {
        val value = classOf(fields.getOrNull(1).orEmpty()) ?: continue
        val (first, last) = parseRange(fields[0])
        for (cp in first..last) gcb[cp] = value
    }

    // Extended_Pictographic is a separate file and a separate property. It only
    // ever applies where the break class is Other, so it never overwrites one.
    for (fields in ucdLines("emoji-data.txt")) {
        if (fields.getOrNull(1) != "Extended_Pictographic") continue
        val (first, last) = parseRange(fields[0])
        for (cp in first..last) {
            if (gcb[cp] == null) gcb[cp] = GraphemeBreakClass.EXTENDED_PICTOGRAPHIC
        }
    }

    val incb = HashMap<Int, IndicConjunctBreak>()
    for (fields in ucdLines("IndicConjunctBreak.txt")) {
        if (fields.getOrNull(1) != "InCB") continue
        val value = when (fields.getOrNull(2)) {
            "Consonant" -> IndicConjunctBreak.CONSONANT
            "Linker" -> IndicConjunctBreak.LINKER
            "Extend" -> IndicConjunctBreak.EXTEND
            else -> continue
        }
        val (first, last) = parseRange(fields[0])
        for (cp in first..last) incb[cp] = value
    }

    check(gcb.isNotEmpty() && incb.isNotEmpty()) { "the UCD property files parsed to nothing" }

    val interesting = (gcb.keys + incb.keys).toSortedSet()
    val merged = ArrayList<BreakRange>()
    for (cp in interesting) {
        val g = gcb[cp] ?: GraphemeBreakClass.OTHER
        val i = incb[cp] ?: IndicConjunctBreak.NONE
        val last = merged.lastOrNull()
        if (last != null && last.last == cp - 1 && last.gcb == g && last.incb == i) {
            merged[merged.lastIndex] = BreakRange(last.first, cp, g, i)
        } else {
            merged.add(BreakRange(cp, cp, g, i))
        }
    }
    println("[codegen] grapheme break: ${merged.size} ranges from Unicode $UCD_VERSION")
    return merged
}

/**
 * The ranges as one string: `first,length,gcb,incb` per entry, in ascending
 * order so the runtime can binary search it.
 *
 * Code points are written in base 36 and the length is a delta rather than an
 * absolute end, both because the table is mostly short ranges and this is what
 * keeps it near twenty kilobytes rather than fifty.
 */
fun encodeGraphemeBreakRanges(ranges: List<BreakRange>): String = ranges.joinToString(LIST_SEPARATOR) { range ->
    val length = range.last - range.first
    val flags = range.gcb.ordinal * 4 + range.incb.ordinal
    range.first.toString(36) + "," + length.toString(36) + "," + flags.toString(36)
}
