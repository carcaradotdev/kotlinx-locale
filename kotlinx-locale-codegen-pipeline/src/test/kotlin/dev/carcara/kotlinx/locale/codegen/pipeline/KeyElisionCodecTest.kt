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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val FS = RecordFormat.FIELD
private const val ES = RecordFormat.ENTRY
private const val KS = RecordFormat.KEY

/**
 * The three cases below the property test are the bugs the Python prototype
 * shipped before the validator existed. Each is worth a named test, because each
 * looked correct when the encoder was read rather than run.
 */
class KeyElisionCodecTest {

    private val codec = KeyElisionCodec()
    private val resolved = PayloadShape.Resolved
    private val sparse = PayloadShape(sparseFields = 2)

    @Test
    fun theKeysLeaveTheRecordsAndTheTableGetsSmaller() {
        val table = mapOf(
            "en" to "America/Sao_Paulo${KS}Sao Paulo${ES}America/Bogota${KS}Bogota",
            "pt" to "America/Sao_Paulo${KS}São Paulo${ES}America/Bogota${KS}Bogotá",
            "es" to "America/Bogota${KS}Bogotá",
        )
        val report = PayloadCodecValidator.require(codec, resolved, "TimeZoneCities", table)
        assertTrue(report.isValid)
        assertTrue(
            report.encoded.payloadByTag.values.none { "America/" in it },
            "the zone ids are still in the records: ${report.encoded.payloadByTag.values}",
        )
        assertTrue(report.after.utf16 < report.before.utf16, "$report")
    }

    @Test
    fun aSingleEmptyValueSurvives() {
        // "".split(ES) is [] rather than [""], so the count has to come from the
        // bitmap. This dropped a GMT entry in the prototype.
        val table = mapOf("en" to "GMT$KS")
        assertTrue(PayloadCodecValidator.require(codec, resolved, "t", table).isValid)
    }

    @Test
    fun aFieldMixingKeyedAndPositionalEntriesSurvives() {
        // Thirty-three locales do this in LocaleDisplayNames.
        val table = mapOf("en" to "US${KS}United States${ES}lowercase")
        val report = PayloadCodecValidator.require(codec, resolved, "t", table)
        assertTrue(report.isValid)
        assertEquals(
            "lowercase",
            PayloadRecord.parse(codec.decode(resolved, report.encoded).getValue("en"), resolved).positional(0).single(),
        )
    }

    @Test
    fun recordsKeepTheirOwnFieldCount() {
        // Padding every record out to the widest one added separators the
        // original never had, which the runtime reads as empty fields.
        val table = mapOf(
            "en" to "a${KS}1${FS}b${KS}2",
            "pt" to "a${KS}3",
        )
        val report = PayloadCodecValidator.require(codec, resolved, "t", table)
        assertTrue(report.isValid)
        assertEquals(1, codec.decode(resolved, report.encoded).getValue("pt").count { it == FS.single() } + 1)
    }

    @Test
    fun aRepeatedKeyKeepsTheAnswerTheRuntimeWouldGive() {
        // Five locales ship one. The runtime returns the first, so the codec has
        // to agree rather than pick the last.
        val table = mapOf("en" to "s${KS}first${ES}s${KS}second")
        val report = PayloadCodecValidator.require(codec, resolved, "t", table)
        assertTrue(report.isValid)
        assertEquals("first", PayloadRecord.parse(codec.decode(resolved, report.encoded).getValue("en"), resolved).lookup(0, "s"))
    }

    @Test
    fun sparseRecordsKeepTheirParent() {
        val table = mapOf(
            "en" to "root${FS}TG${KS}Togo",
            "en-GB" to "en${FS}US${KS}the States",
        )
        val report = PayloadCodecValidator.require(codec, sparse, "CountryNames", table)
        assertTrue(report.isValid)
        assertEquals("en", PayloadRecord.parse(codec.decode(sparse, report.encoded).getValue("en-GB"), sparse).parent)
    }

    @Test
    fun aValueMayContainTheKeySeparator() {
        val table = mapOf("en" to "s$KS{0} s$KS{0} sec")
        assertTrue(PayloadCodecValidator.require(codec, resolved, "DurationUnits", table).isValid)
    }

    @Test
    fun randomTablesRoundTrip() {
        val random = Random(20260810)
        repeat(200) { iteration ->
            val keys = (0 until random.nextInt(1, 12)).map { "k$it" }
            val table = (0 until random.nextInt(1, 8)).associate { locale ->
                val fields = (0 until random.nextInt(1, 3)).map {
                    keys.filter { random.nextBoolean() }
                        .joinToString(ES) { key -> key + KS + random.nextInt(0, 100) }
                }
                "l$locale" to fields.joinToString(FS)
            }
            val report = PayloadCodecValidator.validate(codec, resolved, "t$iteration", table)
            assertTrue(report.isValid, "iteration $iteration: ${report.failures}")
        }
    }
}
