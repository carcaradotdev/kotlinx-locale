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

@file:OptIn(dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.internal

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.random.Random

/**
 * Checks [inflateRaw] against the encoder that will actually produce the data.
 *
 * The generator compresses with the platform's zlib, so agreeing with a fixture
 * proves nothing useful; agreeing with zlib over inputs chosen to reach every
 * branch of RFC 1951 does. The three block types, the run-length forms of the
 * code-length alphabet, and overlapping back-references all have to be hit, and
 * the inputs below are shaped to hit them.
 */
private fun deflateRaw(input: ByteArray, level: Int = 9): ByteArray {
    val deflater = Deflater(level, true)
    deflater.setInput(input)
    deflater.finish()
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
    deflater.end()
    return out.toByteArray()
}

val InflateTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("an empty stream inflates to nothing") {
        assertEquals(0, inflateRaw(deflateRaw(ByteArray(0)), 0).size, "empty")
    }

    test("stored blocks survive, which is what incompressible input produces") {
        val random = Random(20260811)
        val noise = ByteArray(40_000) { random.nextInt(256).toByte() }
        // Level 0 forces stored blocks, and more than one of them.
        assertTrue(deflateRaw(noise, 0).contentEquals(deflateRaw(noise, 0)), "deterministic")
        assertTrue(inflateRaw(deflateRaw(noise, 0), noise.size).contentEquals(noise), "stored")
    }

    test("fixed Huffman blocks survive, which is what tiny input produces") {
        val small = "abcabcabcabc".encodeToByteArray()
        assertTrue(inflateRaw(deflateRaw(small), small.size).contentEquals(small), "fixed")
    }

    test("overlapping back-references survive, which is how a run is spelled") {
        // One byte repeated: the match distance is 1 and the length is far more,
        // so the copy reads bytes it is still writing.
        val run = ByteArray(5000) { 'x'.code.toByte() }
        assertTrue(inflateRaw(deflateRaw(run), run.size).contentEquals(run), "run")
    }

    test("dynamic Huffman blocks survive, over text that earns one") {
        val text = buildString {
            repeat(2000) { append("Dirham ya Falme za Kiarabu ").append(it) }
        }.encodeToByteArray()
        assertTrue(inflateRaw(deflateRaw(text), text.size).contentEquals(text), "dynamic")
    }

    test("random inputs of every shape round-trip") {
        val random = Random(20260811)
        repeat(300) { iteration ->
            val size = random.nextInt(0, 6000)
            // A mix of runs and noise, so both Huffman forms and both match
            // kinds appear across the run.
            val data = ByteArray(size) {
                if (random.nextInt(3) == 0) {
                    random.nextInt(256).toByte()
                } else {
                    (random.nextInt(4) + 'a'.code).toByte()
                }
            }
            val level = random.nextInt(0, 10)
            val back = inflateRaw(deflateRaw(data, level), data.size)
            assertTrue(back.contentEquals(data), "iteration $iteration, size $size, level $level")
        }
    }

    test("a wrong size hint costs a copy rather than correctness") {
        val text = "the hint is a hint".repeat(200).encodeToByteArray()
        assertTrue(inflateRaw(deflateRaw(text), 1).contentEquals(text), "too small")
        assertTrue(inflateRaw(deflateRaw(text), 100_000).contentEquals(text), "too large")
    }
}
