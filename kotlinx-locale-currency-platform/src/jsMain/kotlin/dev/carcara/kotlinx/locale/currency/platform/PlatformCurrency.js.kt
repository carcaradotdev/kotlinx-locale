package dev.carcara.kotlinx.locale.currency.platform

/**
 * External declarations rather than `js("...")` strings, because a string cannot
 * see this file's function parameters on Kotlin/JS and would silently format
 * undefined.
 */
@JsName("Intl")
private external object Intl {
    class NumberFormat(locales: Array<String>, options: dynamic) {
        fun format(value: String): String
        fun formatToParts(value: String): Array<dynamic>
    }

    class DisplayNames(locales: Array<String>, options: dynamic) {
        fun of(code: String): String?
    }
}

private fun numberFormat(currencyCode: String, localeTag: String, useIsoCode: Boolean, accounting: Boolean): Intl.NumberFormat {
    val options: dynamic = js("({ style: 'currency' })")
    options.currency = currencyCode
    options.currencyDisplay = if (useIsoCode) "code" else "symbol"
    options.currencySign = if (accounting) "accounting" else "standard"
    return Intl.NumberFormat(arrayOf(localeTag), options)
}

internal actual fun platformCurrencySymbol(currencyCode: String, localeTag: String): String? = try {
    // Intl exposes no symbol getter, so the symbol is read out of a formatted
    // zero: the part tagged "currency" is exactly what it would have written.
    numberFormat(currencyCode, localeTag, useIsoCode = false, accounting = false)
        .formatToParts("0")
        .firstOrNull { it.type == "currency" }
        ?.value as? String
} catch (_: Throwable) {
    null
}

internal actual fun platformCurrencyName(currencyCode: String, localeTag: String): String? = try {
    val options: dynamic = js("({ type: 'currency', fallback: 'none' })")
    Intl.DisplayNames(arrayOf(localeTag), options).of(currencyCode)
} catch (_: Throwable) {
    null
}

internal actual fun platformFormatCurrency(
    amount: String,
    currencyCode: String,
    localeTag: String,
    useIsoCode: Boolean,
    accounting: Boolean,
): String? = try {
    // A string, not a Double: ECMA-402 accepts an exact decimal string and Long
    // minor units run past what a Double holds exactly.
    numberFormat(currencyCode, localeTag, useIsoCode, accounting).format(amount)
} catch (_: Throwable) {
    null
}

/** ECMA-402 has no parser at all, so this is always a miss. */
internal actual fun platformParseCurrency(text: String, currencyCode: String, localeTag: String): String? = null
