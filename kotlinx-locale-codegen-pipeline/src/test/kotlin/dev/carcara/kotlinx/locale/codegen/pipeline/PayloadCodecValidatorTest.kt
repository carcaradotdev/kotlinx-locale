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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val FS = RecordFormat.FIELD
private const val ES = RecordFormat.ENTRY
private const val KS = RecordFormat.KEY

/**
 * What the validator has to catch.
 *
 * Each broken codec here is a bug that was actually written while this was being
 * worked out, not an invented one. A validator that misses any of them lets a
 * table ship with translations quietly missing, so each gets a test.
 */
class PayloadCodecValidatorTest {

    private val resolved = PayloadShape.Resolved
    private val sparse = PayloadShape(sparseFields = 2)

    private val table = mapOf(
        "en" to "US${KS}United States${ES}TG${KS}Togo",
        "pt" to "US${KS}Estados Unidos${ES}TG${KS}Togo",
    )

    @Test
    fun theIdentityCodecChangesNothingAndSaysSo() {
        val report = PayloadCodecValidator.require(PayloadCodec.Identity, resolved, "t", table)
        assertTrue(report.isValid)
        assertEquals(table, report.encoded.payloadByTag)
        assertEquals(report.before.utf8, report.after.utf8)
    }

    @Test
    fun aCodecThatDropsALocaleFails() {
        val report = PayloadCodecValidator.validate(dropping("pt"), resolved, "t", table)
        assertFalse(report.isValid)
        assertTrue(report.failures.single().contains("dropped 1 tags"), report.failures.toString())
    }

    @Test
    fun aCodecThatLosesAnEntryFails() {
        val broken = mapping { it.substringBefore(ES) }
        val report = PayloadCodecValidator.validate(broken, resolved, "t", table)
        assertFalse(report.isValid)
        assertTrue(report.failures.any { "lost keys" in it }, report.failures.toString())
    }

    @Test
    fun aCodecThatChangesAValueFails() {
        val broken = mapping { it.replace("Togo", "Tonga") }
        val report = PayloadCodecValidator.validate(broken, resolved, "t", table)
        assertFalse(report.isValid)
        assertTrue(report.failures.any { "became" in it }, report.failures.toString())
    }

    /**
     * The one a byte comparison gets wrong. Storing values in key order is what
     * the useful codecs do, and no lookup can see it, so it has to pass.
     */
    @Test
    fun reorderingKeyedEntriesIsAllowed() {
        val reversing = mapping { record ->
            record.split(ES).reversed().joinToString(ES)
        }
        assertTrue(PayloadCodecValidator.validate(reversing, resolved, "t", table).isValid)
    }

    /**
     * The one a key-by-key comparison gets wrong. A field may hold entries with
     * no key at all, and their order is the only thing that identifies them.
     */
    @Test
    fun droppingPositionalEntriesFails() {
        val mixed = mapOf("en" to "US${KS}United States${ES}lower")
        val broken = mapping { it.substringBefore(ES) }
        val report = PayloadCodecValidator.validate(broken, resolved, "t", mixed)
        assertFalse(report.isValid)
        assertTrue(report.failures.any { "positional" in it }, report.failures.toString())
    }

    @Test
    fun losingTheParentOfASparseRecordFails() {
        val chained = mapOf("en-GB" to "en${FS}TG${KS}Togo")
        val broken = mapping { it.substringAfter(FS) }
        val report = PayloadCodecValidator.validate(broken, sparse, "t", chained)
        assertFalse(report.isValid)
    }

    @Test
    fun requireThrowsRatherThanReturningAFailedReport() {
        assertFailsWith<PayloadCodecException> {
            PayloadCodecValidator.require(dropping("pt"), resolved, "t", table)
        }
    }

    @Test
    fun theReportMeasuresEveryUnitSeparately() {
        // "Estados Unidos" is ASCII; a non-ASCII value costs more in UTF-8 than
        // in UTF-16, which is the whole reason three numbers are carried.
        val cyrillic = mapOf("ru" to "US${KS}Соединённые Штаты")
        val report = PayloadCodecValidator.require(PayloadCodec.Identity, resolved, "t", cyrillic)
        assertTrue(report.before.utf8 > report.before.utf16 / 2)
        assertEquals(report.before.mutf8, report.before.utf8)
    }

    private fun mapping(transform: (String) -> String): PayloadCodec = object : PayloadCodec {
        override val id: String get() = "test"
        override fun encode(shape: PayloadShape, payloads: Map<String, String>) =
            EncodedPayloads(payloads.mapValues { (_, v) -> transform(v) })
        override fun decode(shape: PayloadShape, encoded: EncodedPayloads) = encoded.payloadByTag
    }

    private fun dropping(tag: String): PayloadCodec = object : PayloadCodec {
        override val id: String get() = "test"
        override fun encode(shape: PayloadShape, payloads: Map<String, String>) = EncodedPayloads(payloads - tag)
        override fun decode(shape: PayloadShape, encoded: EncodedPayloads) = encoded.payloadByTag
    }
}
