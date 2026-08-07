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

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
import dev.carcara.kotlinx.locale.currency.currencies
import dev.carcara.kotlinx.locale.currency.currency

/**
 * Touches every public declaration of `dev.carcara:kotlinx-locale-currency`:
 * the `Currency` enum and its lookups, the `CurrencyAmount` value type with its
 * formatting and parsing, the symbol styles and the `Country` extensions.
 * Iterating the enum with `symbol`/`displayName` is what pulls in the CLDR
 * currency name, symbol and pattern tables.
 */
@JsExport
fun currencySurface(tag: String, code: String, numericCode: Int, minorUnits: Double, text: String): String {
    val locale = Locale.forLanguageTag(tag)
    val units = minorUnits.toLong()

    return buildString {
        for (currency in Currency.entries) {
            append(currency.name)
            append(currency.code)
            append(currency.numericCode)
            append(currency.defaultFractionDigits)
            append(currency.minorUnitDigits)
            append(currency.cldrFractionDigits)
            append(currency.cldrRoundingIncrement)
            append(currency.cldrCashFractionDigits)
            append(currency.cldrCashRoundingIncrement)
            append(currency.symbol(locale))
            append(currency.symbol())
            append(currency.displayName(locale))
            append(currency.displayName())
            append(currency.isoToCldrUnits(units))
            append(currency.cldrToIsoUnits(units))
        }
        append(Currency.valueOf(code).numericCode)
        append(Currency.forCode(code).name)
        append(Currency.forCodeOrNull(code)?.name)
        append(Currency.forNumericCode(numericCode).name)
        append(Currency.forNumericCodeOrNull(numericCode)?.name)
        append(Currency.forLocaleOrNull(locale)?.name)
        append(Currency.forLocaleOrNull()?.name)

        for (country in Country.entries) {
            append(country.currencies.size)
            append(country.currency?.code)
            append(Currency.forCountryOrNull(country)?.code)
        }

        val amount = CurrencyAmount(Currency.forCode(code), units)
        val other = CurrencyAmount.of(Currency.forCode(code), units)
        val third = CurrencyAmount.of(Currency.forCode(code), units, numericCode)
        append(amount.currency.code)
        append(amount.minorUnits)
        append(amount.majorUnits)
        append(amount.minorPart)
        append((amount + other).minorUnits)
        append((amount - other).minorUnits)
        append((-amount).minorUnits)
        append(amount.compareTo(third))
        append(amount == other)
        append(amount.hashCode())
        append(amount.toString())
        append(amount.toDecimalString())
        append(CurrencyAmount.parseOrNull(amount.currency, text)?.minorUnits)
        append(CurrencyAmount.parse(amount.currency, text).minorUnits)
        append(CurrencyAmount.parseFormattedOrNull(amount.currency, text, locale)?.minorUnits)
        append(CurrencyAmount.parseFormattedOrNull(amount.currency, text)?.minorUnits)
        append(CurrencyAmount.parseFormatted(amount.currency, text, locale).minorUnits)
        append(CurrencyAmount.parseFormatted(amount.currency, text).minorUnits)

        for (style in CurrencySymbolStyle.entries) {
            append(style.name)
            append(style.ordinal)
            append(CurrencySymbolStyle.valueOf(style.name).ordinal)
            for (accounting in listOf(false, true)) {
                for (cash in listOf(false, true)) {
                    append(amount.format(locale, style, accounting, cash))
                }
            }
        }
        append(amount.format())
        append(amount.format(locale))
        append(amount.format(locale, CurrencySymbolStyle.CODE))
    }
}
