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
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
import dev.carcara.kotlinx.locale.currency.forCode
import dev.carcara.kotlinx.locale.currency.platform.displayName
import dev.carcara.kotlinx.locale.currency.platform.format
import dev.carcara.kotlinx.locale.currency.platform.parseFormattedOrNull
import dev.carcara.kotlinx.locale.currency.platform.symbol
import dev.carcara.kotlinx.locale.number.SignDisplay

/**
 * The full currency surface over the host.
 *
 * Call for call probe-currency-full, with the one difference the API itself
 * forces: parsing is `parseFormattedOrNull` here, because the platform has no
 * parser everywhere and the return type says so.
 */
@JsExport
public fun probe(code: String, tag: String, minorUnits: String, text: String): String {
    val locale = Locale.forLanguageTag(tag)
    val money = Currency.forCode(code)
    val amount = CurrencyAmount(money, minorUnits.toLong())
    return listOf(
        amount.format(locale),
        amount.format(locale, CurrencySymbolStyle.CODE, signDisplay = SignDisplay.ACCOUNTING, cash = true),
        money.symbol(locale),
        money.displayName(locale),
        CurrencyAmount.parseFormattedOrNull(money, text, locale)?.toDecimalString(),
    ).joinToString(" ")
}
