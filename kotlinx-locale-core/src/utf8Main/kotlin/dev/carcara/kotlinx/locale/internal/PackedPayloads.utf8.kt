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
 * Seven bits a character, every character below U+0080.
 *
 * These targets store a literal as UTF-8, or the JVM's modified UTF-8, both of
 * which spend one byte on a character under U+0080 and two or three above it.
 * Seven bits is the most that fits in that one-byte range, so the packing needs
 * 8/7 as many characters as there are bytes and every one of them stays cheap.
 * Eight bits a character would need fewer characters and cost more, because half
 * of them would leave the range.
 *
 * U+0000 is skipped, so values land on U+0001 to U+0080: a NUL costs two bytes
 * in a class file's modified UTF-8 rather than one.
 */
@InternalKotlinxLocaleApi
public actual fun unpackPayload(packed: String): ByteArray {
    val out = ByteArray(packed.length * 7 / 8)
    var accumulator = 0
    var bits = 0
    var index = 0
    for (char in packed) {
        accumulator = (accumulator shl 7) or (char.code - 1)
        bits += 7
        while (bits >= 8 && index < out.size) {
            bits -= 8
            out[index++] = ((accumulator shr bits) and 0xFF).toByte()
        }
    }
    return out
}
