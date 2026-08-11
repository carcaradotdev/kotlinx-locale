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

/**
 * Fifteen bits a character, every character at U+0800 or above.
 *
 * Kotlin/Native stores a literal as UTF-16, which spends two bytes on every
 * character alike. Nothing is saved by keeping characters small, so the packing
 * takes the opposite decision from the UTF-8 one and carries as many bits per
 * character as the Basic Multilingual Plane allows. Fifteen bits is the largest
 * power of two that fits once the surrogate block is excluded, and it halves the
 * character count against seven bits.
 *
 * The surrogate range U+D800 to U+DFFF is stepped over. An unpaired surrogate is
 * not a valid character in a source file.
 */
@InternalKotlinxLocaleApi
public actual fun unpackPayload(packed: String): ByteArray {
    val out = ByteArray(packed.length * 15 / 8)
    var accumulator = 0L
    var bits = 0
    var index = 0
    for (char in packed) {
        val code = char.code
        val value = (if (code < 0xD800) code else code - 0x0800) - 0x0800
        accumulator = (accumulator shl 15) or value.toLong()
        bits += 15
        while (bits >= 8 && index < out.size) {
            bits -= 8
            out[index++] = ((accumulator shr bits) and 0xFF).toByte()
        }
    }
    return out
}
