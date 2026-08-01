@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.currency.platform

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.CurrencyFormatOptions
import dev.carcara.kotlinx.locale.currency.CurrencyFormatSource
import dev.carcara.kotlinx.locale.currency.CurrencyNameSource
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
import dev.carcara.kotlinx.locale.currency.displayName
import dev.carcara.kotlinx.locale.currency.forCodeOrNull
import dev.carcara.kotlinx.locale.currency.format
import dev.carcara.kotlinx.locale.currency.parseFormattedOrNull
import dev.carcara.kotlinx.locale.currency.symbol
import dev.carcara.kotlinx.locale.number.NumberNotation
import dev.carcara.kotlinx.locale.number.SignDisplay
import dev.carcara.kotlinx.locale.platform.PlatformLocaleData

internal expect fun platformCurrencySymbol(currencyCode: String, localeTag: String): String?

internal expect fun platformCurrencyName(currencyCode: String, localeTag: String): String?

/**
 * Formats [amount], an exact decimal string such as `-1234.56`.
 *
 * A string rather than a number because the value has to survive the crossing:
 * `Long` minor units go past what a `Double` represents exactly, and every one of
 * the three platform formatters accepts an exact decimal (`BigDecimal`,
 * `Intl.NumberFormat` over a string, `NSDecimalNumber`).
 *
 * [useIsoCode] is how [CurrencySymbolStyle.CODE] is honored. Every one of the
 * three formatters can be told to write the code instead of the symbol, so this
 * is configuration rather than string surgery on the result. A boolean rather
 * than the replacement text because `Intl` takes a mode (`currencyDisplay`)
 * rather than a string, and pretending otherwise would let a caller ask for
 * something two of the three platforms cannot do.
 */
internal expect fun platformFormatCurrency(
    amount: String,
    currencyCode: String,
    localeTag: String,
    useIsoCode: Boolean,
    accounting: Boolean,
): String?

/** Reads [text] back as an exact decimal string, or `null` when the platform cannot. */
internal expect fun platformParseCurrency(text: String, currencyCode: String, localeTag: String): String?

/**
 * Currency symbols, display names and number formatting from the host platform:
 * `java.util.Currency` and `NumberFormat` on JVM and Android, `Intl.NumberFormat`
 * on JS and Wasm/JS, `NSNumberFormatter` on Apple.
 *
 * Partial on purpose, and more partial than [PlatformCountry] is. Three things
 * the hosts do not all do:
 *
 * - **Cash rounding is not a platform concept.** CLDR knows that CHF cash rounds
 *   to 0.05; no platform formatter does. `cash = true` always misses here.
 * - **Accounting is not universal.** `Intl` has `currencySign` and Foundation has
 *   an accounting style, `java.text.NumberFormat` has neither, so on JVM and
 *   Android `accounting = true` misses.
 * - **Parsing is only offered where it is exact.** JVM and Android parse through
 *   `BigDecimal`. `Intl` has no parser at all. Foundation's returns a lossy
 *   `NSNumber`, so rather than round-trip money through a `Double` this reports a
 *   miss and lets a bundled source answer.
 *
 * A miss is not a failure: it is the signal `FallbackCurrencyFormats` reads.
 *
 * ```
 * val formats = FallbackCurrencyFormats(primary = PlatformCurrency, fallback = CldrCurrency)
 * ```
 *
 * Without that composition, an application on JVM would get platform output for
 * ordinary amounts and nothing for accounting ones. With it, the platform answers
 * what it can and CLDR covers the rest, which does mean one screen can mix the
 * two. Whether that matters is the caller's call, and the alternative is
 * inventing an accounting format the host does not have.
 */
public object PlatformCurrency : CurrencyNameSource, CurrencyFormatSource {

    override val supportedLocales: Set<Locale> by lazy {
        PlatformLocaleData.availableLocaleTags()
            .mapNotNullTo(LinkedHashSet()) { Locale.forLanguageTagOrNull(it) }
    }

    /** False on the targets whose platform exposes no locale data at all. */
    public val isAvailable: Boolean
        get() = PlatformLocaleData.isAvailable

    /**
     * The platform's symbol, or `null`.
     *
     * An answer equal to the ISO code is treated as a miss for the same reason it
     * is in the country source: the platforms hand the code back when they have no
     * symbol, `-core` already falls back to the code, and passing the echo through
     * would stop a composing source ever consulting its fallback.
     */
    override fun currencySymbolOrNull(currencyCode: String, locale: Locale): String? =
        platformCurrencySymbol(currencyCode, locale.toLanguageTag())
            ?.takeIf { it.isNotBlank() && !it.equals(currencyCode, ignoreCase = true) }

    override fun currencyNameOrNull(currencyCode: String, locale: Locale): String? =
        platformCurrencyName(currencyCode, locale.toLanguageTag())
            ?.takeIf { it.isNotBlank() && !it.equals(currencyCode, ignoreCase = true) }

    override fun formatOrNull(minorUnits: Long, currencyCode: String, locale: Locale, options: CurrencyFormatOptions): String? {
        // No platform formatter knows about CLDR cash rounding, and none takes a
        // digit count or a compact form through the surface this module has. The
        // honest answer is nothing, so a fallback composer can reach the bundled
        // source rather than have this one guess.
        if (options.cash || options.fractionDigits != null || options.notation != NumberNotation.STANDARD) return null
        if (options.signDisplay != SignDisplay.AUTO && !options.signDisplay.usesAccountingPattern) return null
        val currency = Currency.forCodeOrNull(currencyCode) ?: return null
        return platformFormatCurrency(
            amount = CurrencyAmount(currency, minorUnits).toDecimalString(),
            currencyCode = currencyCode,
            localeTag = locale.toLanguageTag(),
            useIsoCode = options.style == CurrencySymbolStyle.CODE,
            accounting = options.signDisplay.usesAccountingPattern,
        )
    }

    override fun parseToMinorUnitsOrNull(text: String, currencyCode: String, locale: Locale): Long? {
        val currency = Currency.forCodeOrNull(currencyCode) ?: return null
        val decimal = platformParseCurrency(text, currencyCode, locale.toLanguageTag()) ?: return null
        // parseOrNull rejects a fraction the currency cannot hold, which is the
        // same answer the bundled source gives for the same input.
        return CurrencyAmount.parseOrNull(currency, decimal)?.minorUnits
    }
}

/** The platform's symbol for [locale], e.g. `R$`; falls back to the ISO code. */
public fun Currency.symbol(locale: Locale = Locale.current): String = PlatformCurrency.symbol(this, locale)

/** The platform's display name for [locale]; falls back to the ISO code. */
public fun Currency.displayName(locale: Locale = Locale.current): String = PlatformCurrency.displayName(this, locale)

/**
 * Formats the amount with the platform's currency format for [locale].
 *
 * Falls back to `USD 12.50`, the ISO code and the plain decimal, when the
 * platform cannot render it, which on JVM and Android includes every accounting
 * call and everywhere includes every [cash] call, a fraction-digit override and
 * compact notation. Compose with a bundled source to avoid that.
 */
public fun CurrencyAmount.format(
    locale: Locale = Locale.current,
    style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL,
    signDisplay: SignDisplay = SignDisplay.AUTO,
    cash: Boolean = false,
): String = PlatformCurrency.format(this, locale, style, signDisplay, cash)

/** Reads a formatted amount back, or `null` where the platform has no parser. */
public fun CurrencyAmount.Companion.parseFormattedOrNull(
    currency: Currency,
    text: String,
    locale: Locale = Locale.current,
): CurrencyAmount? = PlatformCurrency.parseFormattedOrNull(currency, text, locale)
