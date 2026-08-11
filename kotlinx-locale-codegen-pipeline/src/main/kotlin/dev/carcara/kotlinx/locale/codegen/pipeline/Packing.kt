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

/**
 * How compressed bytes are written down as a Kotlin string.
 *
 * Kotlin has no ByteArray literal. `byteArrayOf(...)` is code rather than a
 * constant, and eleven thousand elements already exceed the JVM's 64 KB method
 * limit, so compressed data has to ride in a String. A String stores characters,
 * so the question is how many bits each character carries, and the answer is not
 * the same on every target:
 *
 * - UTF-8 charges by character value: one byte below U+0080, three at U+0800 and
 *   above. So keep the characters cheap even if that means more of them.
 * - UTF-16 charges two bytes for every character alike. So carry as many bits per
 *   character as possible, since the character itself is free.
 *
 * Hence two packings, and a source set for each.
 */
public enum class Packing(
    /** How many bits of data one character carries. */
    public val bitsPerChar: Int,
) {

    /**
     * Seven bits per character, every character below U+0080.
     *
     * 128 values is exactly the range UTF-8 stores in one byte. Eight bits would
     * spill past U+007F and cost two bytes for half the characters, which is
     * worse than needing 8/7 as many of them.
     *
     * U+0000 is skipped because it costs two bytes in the modified UTF-8 of a
     * class file, so the values land on U+0001..U+0080.
     */
    ASCII7(7) {
        override fun encode(value: Int): Char = (value + 1).toChar()
        override fun decode(char: Char): Int = char.code - 1
    },

    /**
     * Fifteen bits per character, every character at U+0800 or above.
     *
     * 32768 values is the largest power of two that fits in the BMP once the
     * surrogate block is excluded, and unpaired surrogates are not valid UTF-8,
     * so they cannot appear in a source file.
     */
    BMP15(15) {
        override fun encode(value: Int): Char =
            (if (value + BASE < SURROGATE_START) value + BASE else value + BASE + SURROGATE_SIZE).toChar()

        override fun decode(char: Char): Int = (if (char.code < SURROGATE_START) char.code else char.code - SURROGATE_SIZE) - BASE
    },
    ;

    protected abstract fun encode(value: Int): Char

    protected abstract fun decode(char: Char): Int

    public fun pack(data: ByteArray): String = buildString(data.size * 8 / bitsPerChar + 1) {
        var accumulator = 0L
        var bits = 0
        for (byte in data) {
            accumulator = (accumulator shl 8) or (byte.toLong() and 0xFF)
            bits += 8
            while (bits >= bitsPerChar) {
                bits -= bitsPerChar
                append(encode(((accumulator shr bits) and mask).toInt()))
            }
        }
        if (bits > 0) append(encode(((accumulator shl (bitsPerChar - bits)) and mask).toInt()))
    }

    /**
     * The inverse. Trailing padding bits decode to at most one extra byte, which
     * a DEFLATE reader never looks at: the stream ends at its own final block.
     */
    public fun unpack(packed: String): ByteArray {
        val out = ByteArray(packed.length * bitsPerChar / 8)
        var accumulator = 0L
        var bits = 0
        var index = 0
        for (char in packed) {
            accumulator = (accumulator shl bitsPerChar) or decode(char).toLong()
            bits += bitsPerChar
            while (bits >= 8 && index < out.size) {
                bits -= 8
                out[index++] = ((accumulator shr bits) and 0xFF).toByte()
            }
        }
        return out
    }

    private val mask: Long get() = (1L shl bitsPerChar) - 1

    public companion object {
        private const val BASE = 0x0800
        private const val SURROGATE_START = 0xD800
        private const val SURROGATE_SIZE = 0x0800
    }
}
