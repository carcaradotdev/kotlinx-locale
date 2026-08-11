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

/** The longest Huffman code RFC 1951 allows. */
private const val MAX_BITS = 15

/** Extra bits and base value for the 29 length codes, 257..285. */
private val LENGTH_BASE = intArrayOf(
    3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
    35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
)
private val LENGTH_EXTRA = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
    3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
)

/** The same for the 30 distance codes. */
private val DISTANCE_BASE = intArrayOf(
    1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
    257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
)
private val DISTANCE_EXTRA = intArrayOf(
    0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
    7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
)

/** The order RFC 1951 stores the code-length code lengths in. */
private val CODE_LENGTH_ORDER = intArrayOf(
    16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15,
)

/**
 * A canonical Huffman decoder, held as the counts and symbols RFC 1951
 * describes rather than as a tree, which is smaller and needs no allocation
 * per node.
 */
private class Huffman(lengths: IntArray, count: Int) {
    val counts = IntArray(MAX_BITS + 1)
    val symbols = IntArray(count)

    init {
        for (index in 0 until count) counts[lengths[index]]++
        counts[0] = 0
        // offsets[n] is where the symbols of length n start. Built from the
        // counts of every shorter length, which is what makes the code
        // canonical: symbols of one length are consecutive, in symbol order.
        val offsets = IntArray(MAX_BITS + 2)
        for (bits in 1 until MAX_BITS) offsets[bits + 1] = offsets[bits] + counts[bits]
        for (index in 0 until count) {
            if (lengths[index] != 0) symbols[offsets[lengths[index]]++] = index
        }
    }
}

private class BitReader(private val input: ByteArray) {
    var position = 0
    private var bitBuffer = 0
    private var bitCount = 0

    fun bits(need: Int): Int {
        var value = bitBuffer
        while (bitCount < need) {
            value = value or ((input[position++].toInt() and 0xFF) shl bitCount)
            bitCount += 8
        }
        bitBuffer = value ushr need
        bitCount -= need
        return value and ((1 shl need) - 1)
    }

    fun alignToByte() {
        bitBuffer = 0
        bitCount = 0
    }

    fun decode(huffman: Huffman): Int {
        var code = 0
        var first = 0
        var index = 0
        for (length in 1..MAX_BITS) {
            code = code or bits(1)
            val count = huffman.counts[length]
            if (code - first < count) return huffman.symbols[index + (code - first)]
            index += count
            first = (first + count) shl 1
            code = code shl 1
        }
        throw IllegalStateException("malformed DEFLATE stream: no symbol within $MAX_BITS bits")
    }
}

private val FIXED_LITERALS: Huffman by lazy {
    val lengths = IntArray(288)
    for (index in 0..143) lengths[index] = 8
    for (index in 144..255) lengths[index] = 9
    for (index in 256..279) lengths[index] = 7
    for (index in 280..287) lengths[index] = 8
    Huffman(lengths, 288)
}

private val FIXED_DISTANCES: Huffman by lazy {
    Huffman(IntArray(30) { 5 }, 30)
}

/**
 * Inflates a raw DEFLATE stream, RFC 1951.
 *
 * Raw only: a zlib or gzip wrapper is not accepted, and a stream carrying one
 * fails on its header rather than decoding it as a block.
 *
 * [input] may be longer than the stream. Decoding stops at the final block, so
 * trailing bytes are ignored, which is what lets a caller pass a buffer whose
 * last byte holds padding.
 *
 * [expectedSize] sizes the output buffer up front. It is a hint rather than a
 * contract: too small costs a copy, too large costs a trim, and neither changes
 * the result.
 *
 * Throws [IllegalStateException] on a malformed stream.
 */
@InternalKotlinxLocaleApi
public fun inflateRaw(input: ByteArray, expectedSize: Int): ByteArray {
    val reader = BitReader(input)
    var output = ByteArray(if (expectedSize > 0) expectedSize else 1024)
    var written = 0

    fun ensure(extra: Int) {
        if (written + extra <= output.size) return
        var size = if (output.isEmpty()) 1024 else output.size
        while (size < written + extra) size *= 2
        output = output.copyOf(size)
    }

    while (true) {
        val last = reader.bits(1)
        when (val type = reader.bits(2)) {
            0 -> {
                // Stored: byte aligned, a length and its complement, then raw bytes.
                reader.alignToByte()
                val length = (input[reader.position].toInt() and 0xFF) or
                    ((input[reader.position + 1].toInt() and 0xFF) shl 8)
                reader.position += 4
                ensure(length)
                input.copyInto(output, written, reader.position, reader.position + length)
                reader.position += length
                written += length
            }

            1, 2 -> {
                val literals: Huffman
                val distances: Huffman
                if (type == 1) {
                    literals = FIXED_LITERALS
                    distances = FIXED_DISTANCES
                } else {
                    val literalCount = reader.bits(5) + 257
                    val distanceCount = reader.bits(5) + 1
                    val codeLengthCount = reader.bits(4) + 4
                    val codeLengths = IntArray(19)
                    for (index in 0 until codeLengthCount) {
                        codeLengths[CODE_LENGTH_ORDER[index]] = reader.bits(3)
                    }
                    val codeLengthHuffman = Huffman(codeLengths, 19)

                    val lengths = IntArray(literalCount + distanceCount)
                    var index = 0
                    while (index < lengths.size) {
                        when (val symbol = reader.decode(codeLengthHuffman)) {
                            16 -> {
                                val previous = lengths[index - 1]
                                repeat(3 + reader.bits(2)) { lengths[index++] = previous }
                            }
                            17 -> repeat(3 + reader.bits(3)) { lengths[index++] = 0 }
                            18 -> repeat(11 + reader.bits(7)) { lengths[index++] = 0 }
                            else -> lengths[index++] = symbol
                        }
                    }
                    literals = Huffman(lengths, literalCount)
                    distances = Huffman(lengths.copyOfRange(literalCount, lengths.size), distanceCount)
                }

                while (true) {
                    val symbol = reader.decode(literals)
                    if (symbol < 256) {
                        ensure(1)
                        output[written++] = symbol.toByte()
                    } else if (symbol == 256) {
                        break
                    } else {
                        val lengthCode = symbol - 257
                        val length = LENGTH_BASE[lengthCode] + reader.bits(LENGTH_EXTRA[lengthCode])
                        val distanceCode = reader.decode(distances)
                        val distance = DISTANCE_BASE[distanceCode] + reader.bits(DISTANCE_EXTRA[distanceCode])
                        ensure(length)
                        // Byte at a time on purpose: the ranges overlap when the
                        // distance is shorter than the length, which is how
                        // DEFLATE spells a run.
                        var from = written - distance
                        repeat(length) { output[written++] = output[from++] }
                    }
                }
            }

            else -> throw IllegalStateException("malformed DEFLATE stream: reserved block type")
        }
        if (last == 1) break
    }
    return if (written == output.size) output else output.copyOf(written)
}
