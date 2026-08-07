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

package dev.carcara.kotlinx.locale.currency.platform

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParseException
import java.util.Currency as JavaCurrency
import java.util.Locale as JavaLocale

/** Unknown and malformed codes both throw here, and both mean the same thing. */
private fun javaCurrency(code: String): JavaCurrency? = try {
    JavaCurrency.getInstance(code)
} catch (_: IllegalArgumentException) {
    null
}

private fun currencyFormat(currencyCode: String, localeTag: String): Pair<DecimalFormat, JavaCurrency>? {
    val currency = javaCurrency(currencyCode) ?: return null
    val format = NumberFormat.getCurrencyInstance(JavaLocale.forLanguageTag(localeTag)) as? DecimalFormat ?: return null
    format.currency = currency
    // setCurrency is documented not to touch the fraction digits, so the format
    // keeps whatever the locale's currency pattern had, which is 2 for en. BHD has
    // 3 and would silently lose its last minor unit.
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    format.minimumFractionDigits = digits
    format.maximumFractionDigits = digits
    return format to currency
}

internal actual fun platformCurrencySymbol(currencyCode: String, localeTag: String): String? =
    javaCurrency(currencyCode)?.getSymbol(JavaLocale.forLanguageTag(localeTag))

internal actual fun platformCurrencyName(currencyCode: String, localeTag: String): String? =
    javaCurrency(currencyCode)?.getDisplayName(JavaLocale.forLanguageTag(localeTag))

internal actual fun platformFormatCurrency(
    amount: String,
    currencyCode: String,
    localeTag: String,
    useIsoCode: Boolean,
    accounting: Boolean,
): String? {
    // java.text offers no accounting variant. Reporting the miss lets a composing
    // source answer instead of this inventing a format the platform does not have.
    if (accounting) return null
    val (format, _) = currencyFormat(currencyCode, localeTag) ?: return null
    if (useIsoCode) {
        // Setting the currency updates the symbol, so this override has to come
        // after it or it would be overwritten.
        format.decimalFormatSymbols = format.decimalFormatSymbols.apply { currencySymbol = currencyCode }
    }
    return format.format(BigDecimal(amount))
}

internal actual fun platformParseCurrency(text: String, currencyCode: String, localeTag: String): String? {
    val (format, _) = currencyFormat(currencyCode, localeTag) ?: return null
    // Without this the result arrives as a Double and the minor units are a guess.
    format.isParseBigDecimal = true
    return try {
        (format.parse(text) as? BigDecimal)?.toPlainString()
    } catch (_: ParseException) {
        null
    }
}
