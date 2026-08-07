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

@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.NumberNotation
import dev.carcara.kotlinx.locale.number.cldr.numberFormat
import dev.carcara.kotlinx.locale.number.cldr.numberFormatPercent
import dev.carcara.kotlinx.locale.number.cldr.numberOrdinal
import dev.carcara.kotlinx.locale.number.cldr.pluralCategory

/** Numbers, percentages, compact notation, plurals and ordinals. */
@JsExport
public fun probe(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return listOf(
        numberFormat(1234567L, locale),
        numberFormat(1200L, locale, NumberNotation.COMPACT_SHORT),
        numberFormatPercent(Decimal.parse("0.125"), locale, fractionDigits = 1),
        numberOrdinal(1L, locale),
        pluralCategory(3L, locale).name,
    ).joinToString(" ")
}
