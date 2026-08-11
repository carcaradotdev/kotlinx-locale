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

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * Compresses each record and writes the result as a string.
 *
 * Per record rather than per table, which costs about a quarter of the
 * compression and buys back everything that matters at runtime: a lookup
 * inflates one locale instead of eleven hundred, so nothing is paid for locales
 * nobody asks for, and the first read costs microseconds rather than
 * milliseconds.
 *
 * Raw DEFLATE with no zlib wrapper, because the two header bytes and the four
 * checksum bytes buy nothing for data that ships inside the binary that reads
 * it. The reader is `inflateRaw` in the core module.
 *
 * Each record opens with [LENGTH_CHARS] characters giving the inflated byte
 * count, so the reader can size its buffer once rather than grow it.
 */
public class DeflateCodec(private val packing: Packing) : PayloadCodec {

    override val id: String get() = "deflate-" + packing.name.lowercase()

    override fun encode(shape: PayloadShape, payloads: Map<String, String>): EncodedPayloads = EncodedPayloads(
        payloads.mapValues { (_, record) ->
            val raw = record.encodeToByteArray()
            writeLength(raw.size) + packing.pack(deflate(raw))
        },
    )

    override fun decode(shape: PayloadShape, encoded: EncodedPayloads): Map<String, String> =
        encoded.payloadByTag.mapValues { (_, packed) ->
            val size = readLength(packed)
            inflate(packing.unpack(packed.substring(LENGTH_CHARS)), size).decodeToString()
        }

    private fun writeLength(size: Int): String {
        require(size < 1 shl (6 * LENGTH_CHARS)) { "record of $size bytes does not fit the length header" }
        return buildString(LENGTH_CHARS) {
            for (position in LENGTH_CHARS - 1 downTo 0) {
                append(DIGITS[(size shr (6 * position)) and 63])
            }
        }
    }

    private fun readLength(packed: String): Int {
        var size = 0
        for (position in 0 until LENGTH_CHARS) size = size * 64 + DIGITS.indexOf(packed[position])
        return size
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream(input.size / 2 + 32)
        val buffer = ByteArray(8192)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    /**
     * Decoding side, kept here rather than reusing the runtime's inflate: this
     * module has no dependency on the library, and a validator that shared the
     * reader could not catch an encoder and a reader agreeing on the wrong
     * thing.
     */
    private fun inflate(input: ByteArray, size: Int): ByteArray {
        val inflater = java.util.zip.Inflater(true)
        inflater.setInput(input)
        val out = ByteArray(size)
        var written = 0
        while (written < size && !inflater.finished()) {
            val read = inflater.inflate(out, written, size - written)
            if (read == 0 && inflater.needsInput()) break
            written += read
        }
        inflater.end()
        return if (written == size) out else out.copyOf(written)
    }

    public companion object {

        /** Base-64 digits, matching the ones the runtime reads the header with. */
        public const val DIGITS: String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+/"

        /** Four base-64 characters, so a record may inflate to 16 MB. */
        public const val LENGTH_CHARS: Int = 4
    }
}

/**
 * Runs codecs in order, each over what the last produced.
 *
 * Key elision and compression are not alternatives. The keys sit interleaved
 * between the values, so pulling them out does not only delete those bytes, it
 * lets DEFLATE match value text across records it could not reach before. The
 * two together beat either alone, measurably.
 */
public class ChainedCodec(private val codecs: List<PayloadCodec>) : PayloadCodec {

    public constructor(vararg codecs: PayloadCodec) : this(codecs.toList())

    override val id: String get() = codecs.joinToString("+") { it.id }

    override fun encode(shape: PayloadShape, payloads: Map<String, String>): EncodedPayloads {
        var current = EncodedPayloads(payloads)
        val shared = ArrayList<String>()
        for (codec in codecs) {
            val next = codec.encode(shape, current.payloadByTag)
            // Only the first codec in the chain may hoist shared tables out; a
            // later one would have to compress them too, and nothing needs that.
            shared += next.sharedTables
            current = EncodedPayloads(next.payloadByTag)
        }
        return EncodedPayloads(current.payloadByTag, shared)
    }

    override fun decode(shape: PayloadShape, encoded: EncodedPayloads): Map<String, String> {
        var current = encoded.payloadByTag
        for (codec in codecs.reversed()) {
            current = codec.decode(shape, EncodedPayloads(current, encoded.sharedTables))
        }
        return current
    }

    public companion object
}
