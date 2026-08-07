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
import dev.carcara.kotlinx.locale.conformance.GRAPHEME_BREAK_VERSION
import dev.carcara.kotlinx.locale.conformance.graphemeBreakCases
import dev.carcara.kotlinx.locale.conformance.graphemeBreakTable
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Holds the cluster boundaries to Unicode's own conformance file.
 *
 * `GraphemeBreakTest.txt` is the normative test for UAX #29, so passing every
 * case is the difference between implementing the algorithm and approximating
 * it. There are no exclusions and there should never be: a failure here is this
 * library disagreeing with the specification, not with a locale's preference.
 */
@OptIn(InternalKotlinxLocaleApi::class)
class GraphemeClustersTest {

    @BeforeTest
    fun install() {
        GraphemeClusters.install(graphemeBreakTable)
    }

    private fun decode(case: String): Pair<String, List<Int>> {
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

    @Test
    fun everyUnicodeCasePasses() {
        assertTrue(graphemeBreakCases.size > 1000, "the fixture shrank to ${graphemeBreakCases.size}")
        val failures = ArrayList<String>()
        for (case in graphemeBreakCases) {
            val (text, expectedBreaks) = decode(case)
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
