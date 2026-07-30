package dev.carcara.kotlinx.locale.currency.platform

import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyAccountingStyle
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.localizedStringForCurrencyCode

private fun currencyFormatter(currencyCode: String, localeTag: String, accounting: Boolean): NSNumberFormatter = NSNumberFormatter().apply {
    numberStyle = if (accounting) NSNumberFormatterCurrencyAccountingStyle else NSNumberFormatterCurrencyStyle
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
