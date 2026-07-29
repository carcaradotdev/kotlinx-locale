@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.currency.cldr.format

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyFormatSource
import dev.carcara.kotlinx.locale.currency.CurrencyNameSource
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
import dev.carcara.kotlinx.locale.currency.forCodeOrNull
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.resolvedRecord
import dev.carcara.kotlinx.locale.internal.sparseRecordValue
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf

/**
 * A [CurrencyNameSource] over a table of CLDR symbol and name records.
 *
 * Each record is `parent`, the symbols this locale declares, then the names it
 * declares, so one table answers both questions and a lookup walks the parent
 * chain for whatever the locale leaves out.
 */
public class PayloadCurrencyNames(private val records: Map<String, String>) : CurrencyNameSource {

    override val supportedLocales: Set<Locale> by lazy {
        supportedLocalesOf(records)
    }

    override fun currencySymbolOrNull(currencyCode: String, locale: Locale): String? =
        sparseRecordValue(records, locale, field = 1, fieldCount = 3, key = currencyCode)

    override fun currencyNameOrNull(currencyCode: String, locale: Locale): String? =
        sparseRecordValue(records, locale, field = 2, fieldCount = 3, key = currencyCode)
}

/**
 * A [CurrencyFormatSource] over CLDR number-format records and the symbol table
 * the patterns substitute into.
 *
 * Both tables are needed: the pattern comes from [formatRecords] and the `¤`
 * placeholder in it is filled from [nameRecords]. Formatting and parsing return
 * `null` for a code this build's [Currency] does not carry, because the code is
 * what fixes the scale an ISO minor-unit amount sits on.
 */
public class PayloadCurrencyFormats(private val formatRecords: Map<String, String>, nameRecords: Map<String, String>) :
    CurrencyFormatSource {

    private val names = PayloadCurrencyNames(nameRecords)

    override val supportedLocales: Set<Locale> by lazy {
        supportedLocalesOf(formatRecords)
    }

    override fun formatOrNull(
        minorUnits: Long,
        currencyCode: String,
        locale: Locale,
        style: CurrencySymbolStyle,
        accounting: Boolean,
        cash: Boolean,
    ): String? {
        val currency = Currency.forCodeOrNull(currencyCode) ?: return null
        val format = numberFormatFor(locale) ?: return null
        return formatCurrency(format, names, minorUnits, currency, locale, style, accounting, cash)
    }

    override fun parseToMinorUnitsOrNull(text: String, currencyCode: String, locale: Locale): Long? {
        val currency = Currency.forCodeOrNull(currencyCode) ?: return null
        val format = numberFormatFor(locale) ?: return null
        return parseFormattedCurrency(format, names, text, currency, locale)
    }

    private fun numberFormatFor(locale: Locale): CurrencyNumberFormat? = resolvedRecord(formatRecords, locale)?.let(::CurrencyNumberFormat)
}

/**
 * Decoded number-formatting data for one locale, fully resolved at generation
 * time.
 *
 * Public under the internal-API marker so that the ICU cross-check can compare
 * the tables directly. No source interface exposes this, because no platform
 * could implement one that did.
 */
@InternalKotlinxLocaleApi
public class CurrencyNumberFormat(record: String) {
    private val fields = record.split(FIELD_SEPARATOR)

    /** The ten digits of the locale's default numbering system. */
    public val digits: String = fields[0]
    public val decimal: String = fields[1]
    public val group: String = fields[2]

    /** Decimal and group separators used inside currency values (rarely different). */
    public val currencyDecimal: String = fields[3]
    public val currencyGroup: String = fields[4]
    public val minusSign: String = fields[5]
    public val minimumGroupingDigits: Int = fields[6].toIntOrNull() ?: 1
    public val standardPattern: String = fields[7]
    public val standardAlphaPattern: String = fields[8]
    public val accountingPattern: String = fields[9]
    public val accountingAlphaPattern: String = fields[10]
}

/** The number-format record for [locale], for the ICU cross-check. */
@InternalKotlinxLocaleApi
public fun currencyNumberFormatFor(records: Map<String, String>, locale: Locale): CurrencyNumberFormat =
    CurrencyNumberFormat(requireNotNull(resolvedRecord(records, locale)) { "no number format for $locale and no root" })
