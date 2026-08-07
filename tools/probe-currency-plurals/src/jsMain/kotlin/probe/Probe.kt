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
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.cldr.plurals.formatPluralName
import dev.carcara.kotlinx.locale.currency.cldr.plurals.pluralName
import dev.carcara.kotlinx.locale.currency.forCode

/** Currency names that agree with a count, and the amounts written with one. */
@JsExport
public fun probe(code: String, tag: String, minorUnits: String): String {
    val locale = Locale.forLanguageTag(tag)
    val money = Currency.forCode(code)
    return listOf(
        CurrencyAmount(money, minorUnits.toLong()).formatPluralName(locale),
        CurrencyAmount(money, minorUnits.toLong()).formatPluralName(locale, fractionDigits = 0),
        money.pluralName(1, locale),
        money.pluralName(5, locale),
    ).joinToString(" ")
}
