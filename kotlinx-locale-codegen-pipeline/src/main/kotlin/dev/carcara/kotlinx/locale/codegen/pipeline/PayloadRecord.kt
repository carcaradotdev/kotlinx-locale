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
 * One entry of a field: a keyed pair, or a positional value that carries no key.
 *
 * Most fields are entirely keyed. A handful mix the two, and a codec that
 * quietly dropped the positional ones would still pass a key-by-key comparison,
 * which is why they are modelled rather than skipped.
 */
public class PayloadEntry(public val key: String?, public val value: String) {

    override fun toString(): String = if (key == null) value else "$key=$value"

    override fun equals(other: Any?): Boolean = other is PayloadEntry && other.key == key && other.value == value

    override fun hashCode(): Int = 31 * key.hashCode() + value.hashCode()

    public companion object
}

/**
 * A payload record, read the way the runtime reads it.
 *
 * This is the reference model [PayloadCodecValidator] compares against, so it
 * has to agree with `PayloadRecords.kt` on every detail that a lookup can
 * observe, and disagree on nothing else:
 *
 * - a key is the text up to the **first** [RecordFormat.KEY], so a value may
 *   contain that character and several do;
 * - a repeated key resolves to its **first** occurrence, because the runtime
 *   scans a field from the start and returns on the first match, which makes
 *   any later duplicate unreachable;
 * - an empty value is a value, not an absent entry.
 *
 * What a lookup cannot observe is the order of keyed entries, so [answersAs]
 * does not require it. A codec is free to reorder them, and the one that stores
 * values in key order does.
 */
public class PayloadRecord(public val parent: String?, public val fields: List<List<PayloadEntry>>) {

    /** The value the runtime would return for [key] in [field], or null. */
    public fun lookup(field: Int, key: String): String? = fields.getOrNull(field)?.firstOrNull { it.key == key }?.value

    /** The keyed entries of [field], first occurrence winning, as the runtime resolves them. */
    public fun resolvedKeys(field: Int): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (entry in fields.getOrNull(field).orEmpty()) {
            if (entry.key != null) out.putIfAbsent(entry.key, entry.value)
        }
        return out
    }

    /** The positional entries of [field], in order, which order is meaningful for. */
    public fun positional(field: Int): List<String> = fields.getOrNull(field).orEmpty().filter { it.key == null }.map { it.value }

    /**
     * True when no lookup can tell this record from [other].
     *
     * Deliberately not equality: two records that answer every question the same
     * way are interchangeable however their entries are ordered.
     */
    public fun answersAs(other: PayloadRecord): Boolean {
        if (parent != other.parent || fields.size != other.fields.size) return false
        return fields.indices.all { field ->
            resolvedKeys(field) == other.resolvedKeys(field) &&
                positional(field) == other.positional(field)
        }
    }

    /** The record text, which round-trips through [parse] for the same [shape]. */
    public fun render(shape: PayloadShape): String {
        val body = fields.joinToString(RecordFormat.FIELD) { entries ->
            entries.joinToString(RecordFormat.ENTRY) { entry ->
                if (entry.key == null) entry.value else entry.key + RecordFormat.KEY + entry.value
            }
        }
        return if (shape.isSparse) (parent ?: "") + RecordFormat.FIELD + body else body
    }

    override fun toString(): String = "PayloadRecord(parent=$parent, fields=${fields.size})"

    public companion object {

        /** Reads [text] as a record of [shape]. */
        public fun parse(text: String, shape: PayloadShape): PayloadRecord {
            val all = text.split(RecordFormat.FIELD)
            val parent = if (shape.isSparse) all.first() else null
            val body = if (shape.isSparse) all.drop(1) else all
            val fields = body.map { field ->
                field.split(RecordFormat.ENTRY).mapNotNull { entry ->
                    val separator = entry.indexOf(RecordFormat.KEY)
                    when {
                        separator >= 0 -> PayloadEntry(
                            entry.substring(0, separator),
                            entry.substring(separator + 1),
                        )
                        entry.isEmpty() -> null
                        else -> PayloadEntry(null, entry)
                    }
                }
            }
            return PayloadRecord(parent, fields)
        }
    }
}
