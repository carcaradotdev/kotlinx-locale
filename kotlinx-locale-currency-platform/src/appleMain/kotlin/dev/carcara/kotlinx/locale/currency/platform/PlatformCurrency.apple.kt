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
