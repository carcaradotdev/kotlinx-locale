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
 * Turns a packed record back into the text a lookup reads.
 *
 * The generated tables are compressed, and Kotlin has no ByteArray literal, so
 * the compressed bytes ride in a String. How many bits each character carries
 * is the one thing that differs between targets, because the targets bill for a
 * character differently: Kotlin/Native stores every literal as UTF-16 and
 * charges two bytes a character whatever it is, while everything else stores
 * UTF-8 or the JVM's modified UTF-8 and charges by the character's value.
 *
 * So the unpacking is `expect`, with one `actual` per source set, and the
 * generator writes the matching data into the same source set. Everything after
 * unpacking, which is the whole of DEFLATE, is shared.
 */
@InternalKotlinxLocaleApi
public expect fun unpackPayload(packed: String): ByteArray

/** Base-64 digits, matching the ones the generator writes the header with. */
private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+/"

/** Four base-64 characters of inflated byte count, so a record may reach 16 MB. */
private const val LENGTH_CHARS = 4

/**
 * The record for one locale, decompressed.
 *
 * The caller caches the result. At a few microseconds a record this is cheap
 * enough to do on demand and far too expensive to do per lookup, and doing it on
 * demand is the point: an application that asks for three locales never
 * materialises the other eleven hundred.
 */
@OptIn(InternalKotlinxLocaleApi::class)
@InternalKotlinxLocaleApi
public fun decodePayload(packed: String): String {
    var size = 0
    for (position in 0 until LENGTH_CHARS) size = size * 64 + DIGITS.indexOf(packed[position])
    val bytes = unpackPayload(packed.substring(LENGTH_CHARS))
    return inflateRaw(bytes, size).decodeToString(0, size)
}
