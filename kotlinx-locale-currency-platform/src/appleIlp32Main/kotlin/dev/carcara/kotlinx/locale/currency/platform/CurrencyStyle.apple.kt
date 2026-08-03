package dev.carcara.kotlinx.locale.currency.platform

import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyAccountingStyle
import platform.Foundation.NSNumberFormatterCurrencyStyle

/** See the expect in `appleMain`: `NSNumberFormatterStyle` varies in width across Apple targets. */
internal actual fun NSNumberFormatter.applyCurrencyStyle(accounting: Boolean) {
    numberStyle = if (accounting) NSNumberFormatterCurrencyAccountingStyle else NSNumberFormatterCurrencyStyle
}
