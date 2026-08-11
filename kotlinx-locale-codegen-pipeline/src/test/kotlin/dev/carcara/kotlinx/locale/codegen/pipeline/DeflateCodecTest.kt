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

package dev.carcara.kotlinx.locale.codegen.pipeline

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val FS = RecordFormat.FIELD
private const val ES = RecordFormat.ENTRY
private const val KS = RecordFormat.KEY

class PackingTest {

    @Test
    fun ascii7KeepsEveryCharacterInUtf8sOneByteRange() {
        val random = Random(20260811)
        val data = ByteArray(4000) { random.nextInt(256).toByte() }
        val packed = Packing.ASCII7.pack(data)
        assertTrue(packed.all { it.code in 1..0x80 }, "ascii7 left the one-byte range")
        assertTrue(Packing.ASCII7.unpack(packed).copyOf(data.size).contentEquals(data), "round-trip")
    }

    @Test
    fun bmp15KeepsEveryCharacterOutOfTheSurrogateBlock() {
        val random = Random(20260811)
        val data = ByteArray(4000) { random.nextInt(256).toByte() }
        val packed = Packing.BMP15.pack(data)
        assertTrue(packed.none { it.code in 0xD800..0xDFFF }, "bmp15 emitted a surrogate")
        assertTrue(packed.all { it.code >= 0x800 }, "bmp15 emitted a cheap character, wasting bits")
        assertTrue(Packing.BMP15.unpack(packed).copyOf(data.size).contentEquals(data), "round-trip")
    }

    @Test
    fun bmp15NeedsAboutHalfAsManyCharactersAsAscii7() {
        val data = ByteArray(1500) { it.toByte() }
        val ascii7 = Packing.ASCII7.pack(data).length
        val bmp15 = Packing.BMP15.pack(data).length
        // 12000 bits, so ceil(12000/7) against ceil(12000/15). The second
        // divides evenly, which is why this is a ceiling and not a +1.
        assertEquals((1500 * 8 + 6) / 7, ascii7, "ascii7 length")
        assertEquals((1500 * 8 + 14) / 15, bmp15, "bmp15 length")
        assertTrue(bmp15 < ascii7 * 55 / 100, "bmp15 should need about half the characters")
    }

    @Test
    fun everySizeRoundTrips() {
        val random = Random(20260811)
        for (packing in Packing.entries) {
            repeat(200) { size ->
                val data = ByteArray(size) { random.nextInt(256).toByte() }
                val back = packing.unpack(packing.pack(data))
                assertTrue(back.copyOf(size).contentEquals(data), "${packing.name} at $size bytes")
            }
        }
    }
}

class DeflateCodecTest {

    private val shape = PayloadShape(sparseFields = 2)
    private val table = mapOf(
        "en" to "root${FS}US${KS}United States${ES}TG${KS}Togo",
        "pt" to "root${FS}US${KS}Estados Unidos${ES}TG${KS}Togo",
        "ja" to "root${FS}US${KS}アメリカ合衆国${ES}TG${KS}トーゴ",
        "empty" to "root$FS",
    )

    @Test
    fun bothPackingsRoundTripAndShrinkTheTable() {
        for (packing in Packing.entries) {
            val codec = DeflateCodec(packing)
            val report = PayloadCodecValidator.require(codec, shape, "t", table)
            assertTrue(report.isValid, "${packing.name}: ${report.failures}")
        }
    }

    @Test
    fun theChainOfElisionThenDeflateRoundTrips() {
        val codec = ChainedCodec(KeyElisionCodec(), DeflateCodec(Packing.ASCII7))
        val report = PayloadCodecValidator.require(codec, shape, "t", table)
        assertTrue(report.isValid, report.failures.toString())
        assertEquals("key-elision+deflate-ascii7", codec.id)
    }

    @Test
    fun aRealisticTableShrinksOnEveryPlatform() {
        // Enough repetition across records for DEFLATE to have something to do,
        // which is what the real tables look like.
        val many = (0 until 200).associate { index ->
            "l$index" to "root$FS" + (0 until 40).joinToString(ES) { key ->
                "K$key${KS}Country name number $key for locale $index"
            }
        }
        val ascii7 = PayloadCodecValidator.require(
            ChainedCodec(KeyElisionCodec(), DeflateCodec(Packing.ASCII7)),
            shape,
            "t",
            many,
        )
        val bmp15 = PayloadCodecValidator.require(
            ChainedCodec(KeyElisionCodec(), DeflateCodec(Packing.BMP15)),
            shape,
            "t",
            many,
        )
        assertTrue(ascii7.after.utf8 < ascii7.before.utf8 / 2, "ascii7 utf8: ${ascii7.describe()}")
        assertTrue(bmp15.after.utf16 < bmp15.before.utf16 / 2, "bmp15 utf16: ${bmp15.describe()}")
        // Each packing wins on the platform it was chosen for.
        assertTrue(ascii7.after.utf8 < bmp15.after.utf8, "ascii7 should win UTF-8")
        assertTrue(bmp15.after.utf16 < ascii7.after.utf16, "bmp15 should win UTF-16")
    }
}
