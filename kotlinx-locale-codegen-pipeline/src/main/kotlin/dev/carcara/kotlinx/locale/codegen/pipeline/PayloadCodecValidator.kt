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
 * What one codec did to one table.
 *
 * Carries the failures rather than throwing on the first, because a codec that
 * loses one locale and a codec that loses nine hundred are different bugs and
 * the first failure alone does not say which you have.
 */
public class CodecReport(
    public val codec: String,
    public val table: String,
    public val records: Int,
    public val before: PayloadSize,
    public val after: PayloadSize,
    public val failures: List<String>,
    /**
     * What the codec produced, carried so the caller need not encode twice.
     * Validating a table means encoding it, and the tables are megabytes.
     */
    public val encoded: EncodedPayloads,
) {

    public val isValid: Boolean get() = failures.isEmpty()

    /** How much smaller the encoded form is, per platform, as a fraction. */
    public fun saving(unit: (PayloadSize) -> Long): Double {
        val start = unit(before)
        return if (start == 0L) 0.0 else 1.0 - unit(after).toDouble() / start
    }

    public fun describe(): String = buildString {
        append(table).append(": ").append(records).append(" records, ")
        append(codec).append(", ")
        append("utf8 ").append(percent { it.utf8 })
        append(" mutf8 ").append(percent { it.mutf8 })
        append(" utf16 ").append(percent { it.utf16 })
        if (!isValid) append(" — ").append(failures.size).append(" FAILURES")
    }

    private fun percent(unit: (PayloadSize) -> Long): String = String.format("%+.1f%%", -saving(unit) * 100)

    override fun toString(): String = describe()

    public companion object
}

/**
 * Thrown when a codec loses data. Never caught: a build that shipped a lossy
 * table would ship wrong translations, silently, in whichever locale it lost.
 */
public class PayloadCodecException(message: String) : RuntimeException(message) {
    public companion object
}

/**
 * Runs a codec and checks that nothing a lookup can observe has changed.
 *
 * The check is deliberately not "the encoded record decodes to the same string".
 * It is "the decoded record answers every lookup the way the original did",
 * because a codec is allowed to reorder keyed entries and the useful ones do.
 * Getting this distinction wrong in either direction is expensive: too strict
 * and a correct codec looks broken, too loose and a codec that drops the
 * positional entries of a mixed field passes.
 */
public object PayloadCodecValidator {

    public fun validate(codec: PayloadCodec, shape: PayloadShape, table: String, payloads: Map<String, String>): CodecReport {
        val encoded = codec.encode(shape, payloads)
        val decoded = codec.decode(shape, encoded)
        val failures = ArrayList<String>()

        val missing = payloads.keys - decoded.keys
        if (missing.isNotEmpty()) {
            failures += "dropped ${missing.size} tags, first ${missing.take(5)}"
        }
        val extra = decoded.keys - payloads.keys
        if (extra.isNotEmpty()) {
            failures += "invented ${extra.size} tags, first ${extra.take(5)}"
        }

        for ((tag, original) in payloads) {
            val back = decoded[tag] ?: continue
            if (original == back) continue
            val a = PayloadRecord.parse(original, shape)
            val b = PayloadRecord.parse(back, shape)
            if (!a.answersAs(b)) failures += describeMismatch(tag, a, b)
        }

        return CodecReport(
            codec = codec.id,
            table = table,
            records = payloads.size,
            before = PayloadSize.of(payloads.values),
            after = PayloadSize.of(encoded.payloadByTag.values) + PayloadSize.of(encoded.sharedTables),
            failures = failures,
            encoded = encoded,
        )
    }

    /** [validate], but a failure stops the build. */
    public fun require(codec: PayloadCodec, shape: PayloadShape, table: String, payloads: Map<String, String>): CodecReport {
        val report = validate(codec, shape, table, payloads)
        if (!report.isValid) {
            throw PayloadCodecException(
                "codec '${codec.id}' does not round-trip '$table':\n  " +
                    report.failures.take(10).joinToString("\n  ") +
                    if (report.failures.size > 10) "\n  ... and ${report.failures.size - 10} more" else "",
            )
        }
        return report
    }

    private fun describeMismatch(tag: String, a: PayloadRecord, b: PayloadRecord): String {
        if (a.parent != b.parent) return "$tag: parent ${a.parent} became ${b.parent}"
        if (a.fields.size != b.fields.size) {
            return "$tag: ${a.fields.size} fields became ${b.fields.size}"
        }
        for (field in a.fields.indices) {
            val before = a.resolvedKeys(field)
            val after = b.resolvedKeys(field)
            val lost = before.keys - after.keys
            if (lost.isNotEmpty()) return "$tag field $field: lost keys ${lost.take(3)}"
            val gained = after.keys - before.keys
            if (gained.isNotEmpty()) return "$tag field $field: gained keys ${gained.take(3)}"
            val changed = before.keys.firstOrNull { before[it] != after[it] }
            if (changed != null) {
                return "$tag field $field key '$changed': " +
                    "${before[changed]?.take(40)} became ${after[changed]?.take(40)}"
            }
            if (a.positional(field) != b.positional(field)) {
                return "$tag field $field: positional entries changed"
            }
        }
        return "$tag: differs"
    }
}
