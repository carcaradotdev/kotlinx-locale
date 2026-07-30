package dev.carcara.kotlinx.locale.currency.platform

// This target's platform exposes no locale data Kotlin can read, so every lookup
// misses and a consumer composes with a bundled source.

internal actual fun platformCurrencySymbol(currencyCode: String, localeTag: String): String? = null

internal actual fun platformCurrencyName(currencyCode: String, localeTag: String): String? = null

internal actual fun platformFormatCurrency(
    amount: String,
    currencyCode: String,
    localeTag: String,
    useIsoCode: Boolean,
    accounting: Boolean,
): String? = null

internal actual fun platformParseCurrency(text: String, currencyCode: String, localeTag: String): String? = null
