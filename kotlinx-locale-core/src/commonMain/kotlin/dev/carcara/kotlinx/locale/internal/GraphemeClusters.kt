package dev.carcara.kotlinx.locale.internal

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

/**
 * Where one written character ends and the next begins, per UAX #29.
 *
 * A grapheme cluster is what a reader means by "a character", and it is
 * regularly more than one code point: a base plus its combining marks, a Hangul
 * syllable spelled as jamo, a flag as two regional indicators, an emoji joined
 * with a zero-width joiner, and an Indic consonant bound to the next by a
 * virama. Taking the first letter of a name means taking the first cluster.
 *
 * Neither the rules nor the properties are invented here. The rules are the
 * ruleset in CLDR's `common/segments/root.xml`, which since CLDR 44 is the same
 * as the UCD's, and the properties are the vendored UCD files the generator
 * reads. The implementation is held to Unicode's own `GraphemeBreakTest.txt`,
 * every case of it.
 */
@InternalKotlinxLocaleApi
public object GraphemeClusters {

    // The classes, in the order the generated table encodes them.
    private const val OTHER = 0
    private const val CR = 1
    private const val LF = 2
    private const val CONTROL = 3
    private const val EXTEND = 4
    private const val ZWJ = 5
    private const val REGIONAL_INDICATOR = 6
    private const val PREPEND = 7
    private const val SPACING_MARK = 8
    private const val HANGUL_L = 9
    private const val HANGUL_V = 10
    private const val HANGUL_T = 11
    private const val HANGUL_LV = 12
    private const val HANGUL_LVT = 13
    private const val EXTENDED_PICTOGRAPHIC = 14

    private const val INCB_NONE = 0
    private const val INCB_CONSONANT = 1
    private const val INCB_LINKER = 2
    private const val INCB_EXTEND = 3

    private var starts = IntArray(0)
    private var ends = IntArray(0)
    private var flags = IntArray(0)

    /**
     * Installs the property table.
     *
     * Called once by whichever generated artifact carries it. Until then every
     * code point reads as Other, which degrades to breaking between code points
     * rather than to anything wrong: a build with no table still answers, it
     * just cannot see a conjunct.
     */
    @InternalKotlinxLocaleApi
    public fun install(table: String) {
        if (starts.isNotEmpty() || table.isEmpty()) return
        val entries = table.split(ENTRY_SEPARATOR)
        val s = IntArray(entries.size)
        val e = IntArray(entries.size)
        val f = IntArray(entries.size)
        for ((index, entry) in entries.withIndex()) {
            val firstComma = entry.indexOf(',')
            val secondComma = entry.indexOf(',', firstComma + 1)
            val first = entry.substring(0, firstComma).toInt(36)
            val length = entry.substring(firstComma + 1, secondComma).toInt(36)
            s[index] = first
            e[index] = first + length
            f[index] = entry.substring(secondComma + 1).toInt(36)
        }
        starts = s
        ends = e
        flags = f
    }

    private fun flagsOf(codePoint: Int): Int {
        var low = 0
        var high = starts.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            when {
                codePoint < starts[mid] -> high = mid - 1
                codePoint > ends[mid] -> low = mid + 1
                else -> return flags[mid]
            }
        }
        return 0
    }

    private fun classOf(codePoint: Int): Int = flagsOf(codePoint) / 4

    private fun conjunctOf(codePoint: Int): Int = flagsOf(codePoint) % 4

    /**
     * The length in chars of the first grapheme cluster of [text] from [start].
     *
     * Zero only when [start] is at or past the end. The rule numbers in the
     * comments are UAX #29's own, so a disagreement can be checked against the
     * specification rather than against this reading of it.
     */
    @InternalKotlinxLocaleApi
    public fun clusterLengthAt(text: String, start: Int = 0): Int {
        if (start >= text.length) return 0
        var index = start + charCount(text, start)
        if (index >= text.length) return index - start

        // GB9c needs to know whether a linking consonant has been seen, and
        // GB12/13 whether the run of regional indicators so far is even.
        var sawLinkingConsonant = conjunctOf(codePointAt(text, start)) == INCB_CONSONANT
        var sawLinkerSinceConsonant = false
        var regionalIndicators = if (classOf(codePointAt(text, start)) == REGIONAL_INDICATOR) 1 else 0
        // GB11 is `$ExtPict $Extend* $ZWJ x $ExtPict`, so the joiner alone is not
        // enough: the run has to have started with a pictograph. Without this a
        // ZWJ after any character would glue the next emoji to it.
        var pictographBeforeZwj = classOf(codePointAt(text, start)) == EXTENDED_PICTOGRAPHIC

        while (index < text.length) {
            val before = codePointBefore(text, index)
            val after = codePointAt(text, index)
            if (!joins(before, after, sawLinkingConsonant, sawLinkerSinceConsonant, regionalIndicators, pictographBeforeZwj)) {
                break
            }

            val afterClass = classOf(after)
            val afterConjunct = conjunctOf(after)
            when {
                afterConjunct == INCB_CONSONANT -> {
                    sawLinkingConsonant = true
                    sawLinkerSinceConsonant = false
                }
                afterConjunct == INCB_LINKER -> if (sawLinkingConsonant) sawLinkerSinceConsonant = true
                afterConjunct == INCB_EXTEND -> Unit
                else -> {
                    sawLinkingConsonant = false
                    sawLinkerSinceConsonant = false
                }
            }
            regionalIndicators = if (afterClass == REGIONAL_INDICATOR) regionalIndicators + 1 else 0
            pictographBeforeZwj = when (afterClass) {
                EXTENDED_PICTOGRAPHIC -> true
                EXTEND, ZWJ -> pictographBeforeZwj
                else -> false
            }

            index += charCount(text, index)
        }
        return index - start
    }

    /** The first grapheme cluster of [text], or the empty string when it is empty. */
    @InternalKotlinxLocaleApi
    public fun firstCluster(text: String): String = text.substring(0, clusterLengthAt(text, 0))

    /** Every grapheme cluster of [text], in order. */
    @InternalKotlinxLocaleApi
    public fun clusters(text: String): List<String> {
        val result = ArrayList<String>()
        var index = 0
        while (index < text.length) {
            val length = clusterLengthAt(text, index)
            result.add(text.substring(index, index + length))
            index += length
        }
        return result
    }

    /**
     * Whether there is no break between [before] and [after], by the rules of
     * UAX #29 table 1c in the order the specification gives them.
     */
    private fun joins(
        before: Int,
        after: Int,
        sawLinkingConsonant: Boolean,
        sawLinkerSinceConsonant: Boolean,
        regionalIndicators: Int,
        pictographBeforeZwj: Boolean,
    ): Boolean {
        val a = classOf(before)
        val b = classOf(after)

        // GB3: CR × LF. GB4 and GB5: break around every other control.
        if (a == CR && b == LF) return true
        if (a == CONTROL || a == CR || a == LF) return false
        if (b == CONTROL || b == CR || b == LF) return false

        // GB6, GB7, GB8: a Hangul syllable spelled as jamo stays whole.
        if (a == HANGUL_L && (b == HANGUL_L || b == HANGUL_V || b == HANGUL_LV || b == HANGUL_LVT)) return true
        if ((a == HANGUL_LV || a == HANGUL_V) && (b == HANGUL_V || b == HANGUL_T)) return true
        if ((a == HANGUL_LVT || a == HANGUL_T) && b == HANGUL_T) return true

        // GB9, GB9a, GB9b: marks and joiners attach to what precedes them, and
        // a Prepend attaches to what follows.
        if (b == EXTEND || b == ZWJ) return true
        if (b == SPACING_MARK) return true
        if (a == PREPEND) return true

        // GB9c: an Indic conjunct is one cluster. A linking consonant, then any
        // number of extenders, then a linker, then more extenders, joins to the
        // next linking consonant. This is the rule that keeps a Devanagari or
        // Bengali conjunct whole, and Unicode 15.1 adopted it from CLDR.
        if (sawLinkingConsonant && sawLinkerSinceConsonant && conjunctOf(after) == INCB_CONSONANT) return true

        // GB11: an emoji ZWJ sequence. Both ends must be pictographic, and the
        // left one may be separated from the joiner by extenders, which is what
        // pictographBeforeZwj tracks. A bare ZWJ joins nothing.
        if (a == ZWJ && b == EXTENDED_PICTOGRAPHIC && pictographBeforeZwj) return true

        // GB12, GB13: regional indicators pair up, so a flag is one cluster and
        // two flags are two. An odd count so far means this one completes a pair.
        if (a == REGIONAL_INDICATOR && b == REGIONAL_INDICATOR) return regionalIndicators % 2 == 1

        // GB999: otherwise break.
        return false
    }

    private fun charCount(text: String, index: Int): Int =
        if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) 2 else 1

    private fun codePointAt(text: String, index: Int): Int {
        val high = text[index]
        if (high.isHighSurrogate() && index + 1 < text.length) {
            val low = text[index + 1]
            if (low.isLowSurrogate()) {
                return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
            }
        }
        return high.code
    }

    private fun codePointBefore(text: String, index: Int): Int {
        val low = text[index - 1]
        if (low.isLowSurrogate() && index - 2 >= 0) {
            val high = text[index - 2]
            if (high.isHighSurrogate()) {
                return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
            }
        }
        return low.code
    }
}
