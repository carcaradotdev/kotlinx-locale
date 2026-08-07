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

import dev.carcara.kotlinx.locale.catalog.PT
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.cldr.displayName
import dev.carcara.kotlinx.locale.country.forAlpha2
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.cldr.displayName
import dev.carcara.kotlinx.locale.currency.cldr.format
import dev.carcara.kotlinx.locale.currency.forCode
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.cldr.format
import dev.carcara.kotlinx.locale.toLocale
import kotlinx.datetime.LocalDateTime

/** Every domain at once: the ceiling of what this library can cost. */
@JsExport
public fun probe(code: String, currencyCode: String, iso: String, minorUnits: String): String {
    val locale = PT.BR.toLocale()
    val money = Currency.forCode(currencyCode)
    val amount = CurrencyAmount(money, minorUnits.toLong())
    return listOf(
        Country.forAlpha2(code).displayName(locale),
        amount.format(locale),
        money.displayName(locale),
        LocalDateTime.parse(iso).format(FormatStyle.FULL, locale),
    ).joinToString(" ")
}
