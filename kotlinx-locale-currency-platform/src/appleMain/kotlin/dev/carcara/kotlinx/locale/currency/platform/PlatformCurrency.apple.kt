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

import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.localizedStringForCurrencyCode

/**
 * Selects the currency style, which cannot be done here.
 *
 * `numberStyle` is an `NSNumberFormatterStyle`, which is an `NSUInteger`, which
 * is 32 bits wide on watchosArm32 and watchosArm64 and 64 elsewhere. Kotlin refuses a type
 * of varying width in a source set spanning both, so the one line that names it
 * lives in `appleIlp32Main` and `appleLp64Main` instead.
 */
internal expect fun NSNumberFormatter.applyCurrencyStyle(accounting: Boolean)

private fun currencyFormatter(currencyCode: String, localeTag: String, accounting: Boolean): NSNumberFormatter = NSNumberFormatter().apply {
    applyCurrencyStyle(accounting)
    locale = NSLocale(localeIdentifier = localeTag)
    setCurrencyCode(currencyCode)
}

internal actual fun platformCurrencySymbol(currencyCode: String, localeTag: String): String? =
    currencyFormatter(currencyCode, localeTag, accounting = false).currencySymbol

internal actual fun platformCurrencyName(currencyCode: String, localeTag: String): String? =
    NSLocale(localeIdentifier = localeTag).localizedStringForCurrencyCode(currencyCode)

internal actual fun platformFormatCurrency(
    amount: String,
    currencyCode: String,
    localeTag: String,
    useIsoCode: Boolean,
    accounting: Boolean,
): String? {
    val formatter = currencyFormatter(currencyCode, localeTag, accounting)
    // Setting the code updates the symbol, so the override comes after it.
    if (useIsoCode) formatter.setCurrencySymbol(currencyCode)
    // NSDecimalNumber rather than a Double: the amount is exact and has to stay so.
    return formatter.stringFromNumber(NSDecimalNumber(string = amount))
}

/**
 * Always a miss.
 *
 * `numberFromString` hands back an `NSNumber` backed by a `Double`, so round
 * tripping money through it would quietly lose minor units on large amounts.
 * Reporting the miss lets a bundled source, which parses exactly, answer instead.
 */
internal actual fun platformParseCurrency(text: String, currencyCode: String, localeTag: String): String? = null
