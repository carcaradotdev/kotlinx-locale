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

@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.collation

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.collation.cldr.runtime.Collation
import dev.carcara.kotlinx.locale.internal.Normalization
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the collation implementation to Unicode's own conformance file.
 *
 * Temporary shape: the tables are read from disk rather than from generated
 * sources, so the algorithm can be checked before the generator that will emit
 * them is written. The shipped test reads them the way the grapheme break test
 * does, from a generated case data file.
 */
class CollationConformanceJvmTest {

    private val dataDir = File(
        System.getenv("COLLATION_DATA")
            ?: System.getProperty("collation.data")
            ?: "build/collation-data",
    )

    private fun install() {
        Normalization.install(File(dataDir, "norm_table.txt").readText())
        Collation.install(File(dataDir, "root_table.txt").readText())
    }

    @Test
    fun rootCollationOrdersUnicodesOwnConformanceFile() {
        // The generator's inputs come from the pinned CLDR checkout, which a plain
        // `check` does not have. Point -Dcollation.data at it to run these.
        if (!dataDir.isDirectory) { println("skipped: no collation data at $dataDir"); return }
        install()
        val collator = Collation.tailored("")
        val file = File(dataDir, "CollationTest_CLDR_NON_IGNORABLE_SHORT.txt")

        var previous: IntArray? = null
        var previousLine = ""
        var lines = 0
        var outOfOrder = 0
        var firstFailure = ""

        file.forEachLine { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isNotEmpty()) {
                val text = buildString {
                    for (part in line.split(' ')) if (part.isNotEmpty()) appendCodePoint(part.toInt(16))
                }
                val key = collator.sortKey(text)
                lines++
                val last = previous
                if (last != null && compareKeys(last, key) > 0) {
                    outOfOrder++
                    if (firstFailure.isEmpty()) firstFailure = "$previousLine  >  $line"
                }
                previous = key
                previousLine = line
            }
        }

        println("collation conformance: lines=$lines outOfOrder=$outOfOrder")
        assertTrue(lines > 200_000, "expected the full file, read $lines lines")
        assertEquals(0, outOfOrder, "first out of order pair: $firstFailure")
    }

    @Test
    fun accentedNamesSortWithTheirBaseLetter() {
        // The generator's inputs come from the pinned CLDR checkout, which a plain
        // `check` does not have. Point -Dcollation.data at it to run these.
        if (!dataDir.isDirectory) { println("skipped: no collation data at $dataDir"); return }
        install()
        val collator = Collation.tailored("")
        val names = listOf("Ísland", "Írland", "Ítalía", "Indland", "Japan", "Simbabve", "Albanía")
        val sorted = names.sortedWith(collator)

        assertEquals(
            listOf("Albanía", "Indland", "Írland", "Ísland", "Ítalía", "Japan", "Simbabve"),
            sorted,
        )
    }

    @Test
    fun normalisationMatchesUnicodesOwnConformanceFile() {
        // The generator's inputs come from the pinned CLDR checkout, which a plain
        // `check` does not have. Point -Dcollation.data at it to run these.
        if (!dataDir.isDirectory) { println("skipped: no collation data at $dataDir"); return }
        install()
        val file = File(dataDir, "NormalizationTest.txt")

        var cases = 0
        var nfdFailures = 0
        var nfcFailures = 0

        file.forEachLine { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isNotEmpty() && !line.startsWith("@")) {
                val columns = line.split(';')
                val source = columns[0].toText()
                val nfc = columns[1].toText()
                val nfd = columns[2].toText()
                cases++
                // c3 == toNFD(c1) == toNFD(c2) == toNFD(c3)
                for (input in listOf(source, nfc, nfd)) {
                    if (!Normalization.decompose(input).contentEquals(nfd.toCodePointArray())) { nfdFailures++; break }
                }
                // c2 == toNFC(c1) == toNFC(c2) == toNFC(c3)
                for (input in listOf(source, nfc, nfd)) {
                    val composed = Normalization.compose(Normalization.decompose(input))
                    if (!composed.contentEquals(nfc.toCodePointArray())) { nfcFailures++; break }
                }
            }
        }

        println("normalisation conformance: cases=$cases nfd=$nfdFailures nfc=$nfcFailures")
        assertTrue(cases > 19_000, "expected the full file, read $cases cases")
        assertEquals(0, nfdFailures, "NFD failures")
        assertEquals(0, nfcFailures, "NFC failures")
    }

    private fun String.toText(): String = buildString {
        for (part in trim().split(' ')) if (part.isNotEmpty()) appendCodePoint(part.toInt(16))
    }

    private fun String.toCodePointArray(): IntArray = codePoints().toArray()

    private fun compareKeys(a: IntArray, b: IntArray): Int {
        val shared = minOf(a.size, b.size)
        for (i in 0 until shared) if (a[i] != b[i]) return a[i].compareTo(b[i])
        return a.size.compareTo(b.size)
    }
}
