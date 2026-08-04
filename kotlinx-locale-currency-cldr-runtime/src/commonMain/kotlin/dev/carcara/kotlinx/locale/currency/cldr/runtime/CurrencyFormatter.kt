@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.currency.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyFormatOptions
import dev.carcara.kotlinx.locale.currency.CurrencyNameSource
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.currency.displayName
import dev.carcara.kotlinx.locale.currency.minorUnitDigits
import dev.carcara.kotlinx.locale.currency.symbol
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.NumberFormatOptions
import dev.carcara.kotlinx.locale.number.NumberNotation
import dev.carcara.kotlinx.locale.number.PluralCategory
import dev.carcara.kotlinx.locale.number.SignDisplay
import dev.carcara.kotlinx.locale.number.cldr.runtime.AffixSubstitution
import dev.carcara.kotlinx.locale.number.cldr.runtime.CompactPatternTable
import dev.carcara.kotlinx.locale.number.cldr.runtime.FormattedNumberSelector
import dev.carcara.kotlinx.locale.number.cldr.runtime.NumberPattern
import dev.carcara.kotlinx.locale.number.cldr.runtime.formatCompact
import dev.carcara.kotlinx.locale.number.cldr.runtime.isAlphaAdjacent
import dev.carcara.kotlinx.locale.number.cldr.runtime.renderNumber
import dev.carcara.kotlinx.locale.number.internal.rescaleFraction
import dev.carcara.kotlinx.locale.number.internal.roundToIncrement

/**
 * Writes an amount with the pattern and symbols of [locale].
 *
 * The number itself is rendered by the shared engine in
 * `kotlinx-locale-number-cldr-runtime`. What is left here is what is actually
 * about money: choosing between the standard and accounting patterns, switching
 * to the `alphaNextToNumber` variant when the symbol that lands against the
 * digits is a letter, applying the currency's fraction digits and rounding
 * increment, and filling the `¤` placeholder.
 */
internal fun formatCurrency(
    data: CurrencyNumberFormat,
    names: CurrencyNameSource,
    compact: CompactPatternTable,
    selectCategory: FormattedNumberSelector,
    minorUnits: Long,
    currency: Currency,
    locale: Locale,
    options: CurrencyFormatOptions,
): String {
    val currencyText = names.symbol(currency, locale, options.style)

    // CLDR's digit count for the currency, then its rounding increment, then any
    // override the caller asked for. The order matters: the increment is
    // expressed in units of CLDR's last fraction digit.
    val cldrDigits = if (options.cash) currency.cldrCashFractionDigits else currency.cldrFractionDigits
    val increment = if (options.cash) currency.cldrCashRoundingIncrement else currency.cldrRoundingIncrement
    var scaled = rescaleFraction(minorUnits, currency.minorUnitDigits, cldrDigits)
    if (increment > 0) scaled = roundToIncrement(scaled, increment.toLong())
    val amount = Decimal.ofUnscaled(scaled, cldrDigits)
    val digits = options.fractionDigits ?: cldrDigits

    val accounting = options.signDisplay.usesAccountingPattern
    val basePattern = if (accounting) data.accountingPattern else data.standardPattern
    val alphaPattern = if (accounting) data.accountingAlphaPattern else data.standardAlphaPattern
    var pattern = NumberPattern.parse(basePattern)
    if (alphaPattern != basePattern && isAlphaAdjacent(pattern, currencyText)) {
        pattern = NumberPattern.parse(alphaPattern)
    }

    val affix = AffixSubstitution { run ->
        when (run) {
            1 -> currencyText
            2 -> currency.code
            else -> names.displayName(currency, locale)
        }
    }
    val numberOptions = NumberFormatOptions(
        notation = options.notation,
        signDisplay = options.signDisplay,
        grouping = options.grouping,
    )

    val formatted = if (options.notation == NumberNotation.STANDARD || compact.isEmpty) {
        renderNumber(
            value = amount,
            pattern = pattern,
            symbols = data.symbols,
            options = numberOptions,
            fixedFractionDigits = digits,
            useCurrencySeparators = true,
            affix = affix,
            currencySpacing = true,
        )
    } else {
        formatCompact(
            value = amount,
            table = compact,
            standardPattern = pattern,
            symbols = data.symbols,
            selectCategory = selectCategory,
            options = numberOptions,
            fixedFractionDigits = options.fractionDigits,
            useCurrencySeparators = true,
            currencyText = currencyText,
            affix = affix,
            currencySpacing = true,
        )
    }
    return formatted.text
}

/** The plural category of a formatted number, which compact pattern selection needs. */
internal val NO_PLURAL_RULES: FormattedNumberSelector = FormattedNumberSelector { PluralCategory.OTHER }

/** True when [signDisplay] asks for CLDR's accounting pattern. */
internal fun usesAccounting(signDisplay: SignDisplay): Boolean = signDisplay.usesAccountingPattern
