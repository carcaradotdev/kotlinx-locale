package dev.carcara.kotlinx.locale.currency

/** How the currency is written inside a formatted amount. */
public enum class CurrencySymbolStyle {
    /** The localized CLDR symbol, e.g. `$`, `€`, or `US$` for USD in pt-BR. */
    SYMBOL,

    /** The ISO 4217 alphabetic code, e.g. `USD`. */
    CODE,
}
