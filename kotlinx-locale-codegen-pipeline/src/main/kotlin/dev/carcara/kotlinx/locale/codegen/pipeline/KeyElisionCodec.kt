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
 * Takes the keys out of the records.
 *
 * A quarter of the generated data is key text, and every locale repeats the same
 * keys: the same IANA zone ids, the same ISO codes, the same BCP-47 subtags. In
 * `TimeZoneCities` the keys are more than two thirds of the table. They are also
 * ASCII, which costs one byte in a JS bundle and two in a Kotlin/Native binary,
 * so they are a third of what an iOS app pays for the data.
 *
 * So the keys move out. Per field, one sorted universe of every key any locale
 * uses, written once. Each locale then says which of them it has as a bitmap
 * over that universe, and follows it with its values in universe order. The
 * bitmaps repeat too, far harder than the records do, so they are pooled and the
 * record holds an index into the pool.
 *
 * A record goes from
 *
 * ```
 * America/Sao_PauloSão PauloAmerica/BogotaBogotá
 * ```
 *
 * to a pool index and two values. Reading one back is a walk of the universe to
 * find the key, a bit test, and a count of the set bits before it, which says
 * how many values to skip. That is the same order as the scan it replaces, over
 * a string that is shared rather than repeated in every record, so the win here
 * is size rather than speed.
 *
 * The encoded form is the only thing the runtime sees, so `PayloadRecords`
 * decodes exactly this. [decode] exists to prove the encoder did not lose
 * anything, and it caught three bugs that reading the encoder did not: a single
 * empty value, a field mixing keyed and positional entries, and records padded
 * out to the widest field count.
 */
public class KeyElisionCodec : PayloadCodec {

    override val id: String get() = ID

    override fun encode(shape: PayloadShape, payloads: Map<String, String>): EncodedPayloads {
        val records = payloads.mapValues { (_, text) -> PayloadRecord.parse(text, shape) }
        val fieldCount = records.values.maxOfOrNull { it.fields.size } ?: 0

        val universes = (0 until fieldCount).map { field ->
            records.values
                .flatMap { it.fields.getOrNull(field).orEmpty() }
                .mapNotNull { it.key }
                .distinct()
                .sorted()
        }

        val pool = LinkedHashMap<String, Int>()
        val encoded = LinkedHashMap<String, String>(payloads.size)
        for ((tag, record) in records) {
            val parts = record.fields.mapIndexed { field, entries ->
                val universe = universes[field]
                if (universe.isEmpty()) {
                    // Nothing keyed here, so there is nothing to elide. Left
                    // exactly as it was rather than round-tripped through a
                    // model that would reorder it.
                    entries.joinToString(RecordFormat.ENTRY) { it.value }
                } else {
                    val present = LinkedHashMap<String, String>()
                    for (entry in entries) {
                        if (entry.key != null) present.putIfAbsent(entry.key, entry.value)
                    }
                    val bits = Bitmap.of(universe.map { it in present })
                    val index = pool.getOrPut(bits) { pool.size }
                    val values = universe.filter { it in present }.map { present.getValue(it) } +
                        entries.filter { it.key == null }.map { it.value }
                    Index.write(index) + values.joinToString(RecordFormat.ENTRY)
                }
            }
            val body = parts.joinToString(RecordFormat.FIELD)
            encoded[tag] = if (shape.isSparse) (record.parent ?: "") + RecordFormat.FIELD + body else body
        }

        val tables = universes.map { it.joinToString(RecordFormat.ENTRY) } +
            pool.keys.joinToString(RecordFormat.ENTRY)
        return EncodedPayloads(encoded, tables)
    }

    override fun decode(shape: PayloadShape, encoded: EncodedPayloads): Map<String, String> {
        val fieldCount = encoded.sharedTables.size - 1
        val universes = (0 until fieldCount).map { field ->
            encoded.sharedTables[field].let { if (it.isEmpty()) emptyList() else it.split(RecordFormat.ENTRY) }
        }
        val pool = encoded.sharedTables.last()
            .let { if (it.isEmpty()) emptyList() else it.split(RecordFormat.ENTRY) }

        return encoded.payloadByTag.mapValues { (_, text) ->
            val all = text.split(RecordFormat.FIELD)
            val parent = if (shape.isSparse) all.first() else null
            val body = if (shape.isSparse) all.drop(1) else all
            val fields = body.mapIndexed { field, part ->
                val universe = universes.getOrElse(field) { emptyList() }
                if (universe.isEmpty()) {
                    part
                } else {
                    val bits = Bitmap.read(pool[Index.read(part)], universe.size)
                    val rest = part.substring(Index.WIDTH)
                    // A lone empty value renders as an empty field, and splitting
                    // that gives no values rather than one. The bitmap is what
                    // says how many there are.
                    val values = if (bits.count { it } == 0) emptyList() else rest.split(RecordFormat.ENTRY)
                    val keyed = universe.filterIndexed { index, _ -> bits[index] }
                        .mapIndexed { index, key -> key + RecordFormat.KEY + values[index] }
                    (keyed + values.drop(keyed.size)).joinToString(RecordFormat.ENTRY)
                }
            }
            val joined = fields.joinToString(RecordFormat.FIELD)
            if (parent != null) parent + RecordFormat.FIELD + joined else joined
        }
    }

    public companion object {

        public const val ID: String = "key-elision"
    }
}

/**
 * A presence bitmap over a key universe, six bits to a character.
 *
 * Base 64 rather than raw bits because every character here has to survive being
 * a Kotlin string literal, a JS string and a class-file constant, and the
 * printable ASCII range is one byte in all three.
 */
internal object Bitmap {

    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+/"

    fun of(present: List<Boolean>): String = buildString {
        for (start in present.indices step 6) {
            var value = 0
            for (bit in 0 until 6) {
                if (start + bit < present.size && present[start + bit]) value = value or (1 shl bit)
            }
            append(ALPHABET[value])
        }
    }

    fun read(bitmap: String, size: Int): List<Boolean> {
        val bits = ArrayList<Boolean>(size)
        for (index in 0 until size) {
            val value = ALPHABET.indexOf(bitmap[index / 6])
            bits += (value shr (index % 6)) and 1 == 1
        }
        return bits
    }
}

/**
 * The pool index a field opens with.
 *
 * Fixed at two characters, which addresses 4096 distinct key-sets. The largest
 * table in CLDR uses a few hundred, so the width never has to be negotiated
 * between the encoder and the runtime, and the runtime never has to read a
 * header to know where the values start.
 */
internal object Index {

    const val WIDTH: Int = 2

    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+/"

    fun write(index: Int): String {
        require(index < 64 * 64) { "key-set pool overflowed two characters at $index" }
        return "${ALPHABET[index / 64]}${ALPHABET[index % 64]}"
    }

    fun read(field: String): Int = ALPHABET.indexOf(field[0]) * 64 + ALPHABET.indexOf(field[1])
}
