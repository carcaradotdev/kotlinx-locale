package dev.carcara.kotlinx.locale.internal

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.conformance.GRAPHEME_BREAK_VERSION
import dev.carcara.kotlinx.locale.conformance.graphemeBreakCases
import dev.carcara.kotlinx.locale.conformance.graphemeBreakTable
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * Holds the cluster boundaries to Unicode's own conformance file.
 *
 * `GraphemeBreakTest.txt` is the normative test for UAX #29, so passing every
 * case is the difference between implementing the algorithm and approximating
 * it. There are no exclusions and there should never be: a failure here is this
 * library disagreeing with the specification, not with a locale's preference.
 *
 * This lived in `personname-cldr-full` for as long as name monograms were the
 * only caller, which made a failure in the segmenter read as a failure in name
 * formatting. The code is `core/internal/GraphemeClusters.kt`, so the test is
 * here.
 */
@OptIn(InternalKotlinxLocaleApi::class)
val GraphemeClustersConformance by matrixSuite {

    // Registration-time, and deliberately so: the table is global state the
    // segmenter reads, every case below needs it, and installing it once is
    // cheaper than a fixture per case. Nothing mutates it afterwards.
    GraphemeClusters.install(graphemeBreakTable)

    test("every Unicode case passes") {
        assertTrue(graphemeBreakCases.size > 1000, "the fixture shrank to ${graphemeBreakCases.size}")
        val failures = ArrayList<String>()
        for (case in graphemeBreakCases) {
            val (text, expectedBreaks) = decodeGraphemeCase(case)
            // Expected boundaries are code point offsets; convert to char offsets.
            val charOffsetOf = HashMap<Int, Int>()
            var cpIndex = 0
            var charIndex = 0
            while (charIndex <= text.length) {
                charOffsetOf[cpIndex] = charIndex
                if (charIndex == text.length) break
                charIndex += if (text[charIndex].isHighSurrogate() && charIndex + 1 < text.length) 2 else 1
                cpIndex++
            }
            val expected = expectedBreaks.mapNotNull(charOffsetOf::get)

            val actual = ArrayList<Int>()
            var index = 0
            actual.add(0)
            while (index < text.length) {
                index += GraphemeClusters.clusterLengthAt(text, index)
                actual.add(index)
            }
            if (actual != expected) failures += "$case\n   expected $expected got $actual"
        }
        assertTrue(
            failures.isEmpty(),
            "${failures.size} of ${graphemeBreakCases.size} cases disagree with Unicode $GRAPHEME_BREAK_VERSION:\n" +
                failures.take(10).joinToString("\n"),
        )
    }
}

private fun decodeGraphemeCase(case: String): Pair<String, List<Int>> {
    val (points, breaks) = case.split(':')
    val text = buildString {
        for (cp in points.split(',')) appendCodePoint(cp.toInt(36))
    }
    return text to breaks.split(',').filter(String::isNotEmpty).map { it.toInt(36) }
}

private fun StringBuilder.appendCodePoint(cp: Int) {
    if (cp < 0x10000) {
        append(cp.toChar())
    } else {
        val v = cp - 0x10000
        append((0xD800 + (v shr 10)).toChar())
        append((0xDC00 + (v and 0x3FF)).toChar())
    }
}
