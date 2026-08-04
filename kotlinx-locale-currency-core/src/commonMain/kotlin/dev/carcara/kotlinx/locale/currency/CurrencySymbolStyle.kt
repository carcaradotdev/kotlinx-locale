package dev.carcara.kotlinx.locale.currency

/**
 * How the currency is written inside a formatted amount.
 *
 * The four symbol forms are the ones CLDR declares and ICU exposes, and each
 * alternative falls back to [SYMBOL] where a locale declares none, which in turn
 * falls back to the ISO code. Only [SYMBOL] and [VARIANT_SYMBOL] are also read
 * back when parsing; see [CurrencyNameSource] for why the other two are not.
 */
public enum class CurrencySymbolStyle {
    /** The localized CLDR symbol, e.g. `$`, `€`, or `US$` for USD in pt-BR. */
    SYMBOL,

    /**
     * CLDR's `alt="narrow"` spelling, which drops the disambiguating prefix:
     * `$` for USD in pt-BR where [SYMBOL] gives `US$`.
     *
     * Ambiguous by construction, and deliberately so. It is what a column of
     * amounts in one known currency wants, not what a reader can identify a
     * currency from: in en-CA the narrow `$` is the spelling of more than twenty
     * currencies and `£` of five.
     */
    NARROW_SYMBOL,

    /**
     * CLDR's `alt="variant"` spelling, an alternative in real use for a currency
     * whose [SYMBOL] is contested or newly changed, such as `TL` for TRY.
     *
     * The one alternative form that is also accepted when parsing, matching
     * ICU, which feeds its parse tables from `Currencies%variant` but not from
     * the narrow or formal tables.
     */
    VARIANT_SYMBOL,

    /**
     * CLDR's `alt="formal"` spelling, for a locale whose everyday symbol is not
     * what an official document writes.
     *
     * Declared once in CLDR 48: TWD in zh-Hant, where [SYMBOL] is `$` and the
     * formal spelling is `NT$`.
     */
    FORMAL_SYMBOL,

    /** The ISO 4217 alphabetic code, e.g. `USD`. */
    CODE,
}
