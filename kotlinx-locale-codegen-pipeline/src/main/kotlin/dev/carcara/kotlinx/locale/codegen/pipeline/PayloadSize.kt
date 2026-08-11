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
 * What a payload costs, in the three units the platforms actually charge.
 *
 * They disagree, which is the whole reason for measuring three of them. An
 * encoding that trades a five-character name for a three-character code saves
 * seven bytes of [utf8] if the name was non-ASCII and two characters of
 * [utf16], and the second number is the one an iOS binary pays.
 *
 * There is no compressed figure here on purpose. Whether a payload is gzipped,
 * brotlied or shipped raw is the consumer's build pipeline talking, not ours,
 * and a codec that optimises for one compressor is optimising for a guess.
 */
public class PayloadSize(
    /** Kotlin/JS bundle source, and the input to whatever compresses it. */
    public val utf8: Long,
    /** JVM class-file constant pool and the Android dex string pool. */
    public val mutf8: Long,
    /** Kotlin/Native string literals, which are UTF-16 whatever the characters are. */
    public val utf16: Long,
) {

    public operator fun plus(other: PayloadSize): PayloadSize = PayloadSize(utf8 + other.utf8, mutf8 + other.mutf8, utf16 + other.utf16)

    override fun toString(): String = "utf8=${utf8 / 1024}KB mutf8=${mutf8 / 1024}KB utf16=${utf16 / 1024}KB"

    public companion object {

        public val Zero: PayloadSize = PayloadSize(0, 0, 0)

        public fun of(text: String): PayloadSize {
            var utf8 = 0L
            var mutf8 = 0L
            var utf16 = 0L
            var index = 0
            while (index < text.length) {
                val ch = text[index]
                val code = ch.code
                utf16 += 2
                // Where the two UTF-8s part company. A supplementary character
                // is one four-byte sequence in real UTF-8 and two three-byte
                // sequences in the modified UTF-8 a class file uses, so a flag
                // emoji costs a JVM constant pool six bytes and a JS bundle
                // four. Counting per code unit would miss that.
                val high = ch.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()
                if (high) {
                    utf8 += 4
                    mutf8 += 6
                    utf16 += 2
                    index += 2
                    continue
                }
                utf8 += when {
                    code < 0x80 -> 1
                    code < 0x800 -> 2
                    else -> 3
                }
                mutf8 += when {
                    code == 0 -> 2
                    code < 0x80 -> 1
                    code < 0x800 -> 2
                    else -> 3
                }
                index++
            }
            return PayloadSize(utf8, mutf8, utf16)
        }

        public fun of(texts: Iterable<String>): PayloadSize = texts.fold(Zero) { total, text -> total + of(text) }
    }
}
