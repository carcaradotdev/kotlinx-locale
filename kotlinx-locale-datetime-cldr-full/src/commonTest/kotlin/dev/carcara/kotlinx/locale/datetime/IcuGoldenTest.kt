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

@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.datetime

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.conformance.icuGoldenData
import dev.carcara.kotlinx.locale.test.assertEquals

/**
 * Cross-checks the CLDR-generated runtime data against patterns and names
 * extracted from the official ICU repository (a fully independent encoding of
 * the same upstream data). Fields ICU does not define for a locale are null
 * in the golden entry and skipped.
 */
val IcuGoldenTest by matrixSuite {

    // The two sources are point releases of the same upstream data and can
    // disagree on which non-breaking space variant they use (U+00A0 vs U+202F);
    // normalize those before comparing.
    fun String.normalized() = replace(' ', ' ').replace(' ', ' ')
    fun List<String>.normalized() = map { it.normalized() }

    test("runtimeDataMatchesIcu") {
        for (golden in icuGoldenData) {
            val data = localeDataFor(Locale.forLanguageTag(golden.tag))
            golden.dateFormats?.let {
                assertEquals(it.normalized(), data.dateFormats.normalized(), "${golden.tag} dateFormats")
            }
            golden.timeFormats?.let {
                assertEquals(it.normalized(), data.timeFormats.normalized(), "${golden.tag} timeFormats")
            }
            golden.glueFormats?.let {
                assertEquals(it.normalized(), data.glueFormats.normalized(), "${golden.tag} glueFormats")
            }
            golden.monthsWide?.let {
                assertEquals(it.normalized(), data.monthsWide.normalized(), "${golden.tag} monthsWide")
            }
            golden.monthsAbbr?.let {
                assertEquals(it.normalized(), data.monthsAbbr.normalized(), "${golden.tag} monthsAbbr")
            }
            golden.daysWide?.let {
                assertEquals(it.normalized(), data.daysWide.normalized(), "${golden.tag} daysWide")
            }
            golden.daysAbbr?.let {
                assertEquals(it.normalized(), data.daysAbbr.normalized(), "${golden.tag} daysAbbr")
            }
            golden.am?.let { assertEquals(it.normalized(), data.am.normalized(), "${golden.tag} am") }
            golden.pm?.let { assertEquals(it.normalized(), data.pm.normalized(), "${golden.tag} pm") }
            golden.dayPeriods?.forEachIndexed { index, name ->
                if (name != null) {
                    assertEquals(
                        name.normalized(),
                        data.dayPeriodNames[index].normalized(),
                        "${golden.tag} dayPeriods[$index]",
                    )
                }
            }
        }
    }
}
