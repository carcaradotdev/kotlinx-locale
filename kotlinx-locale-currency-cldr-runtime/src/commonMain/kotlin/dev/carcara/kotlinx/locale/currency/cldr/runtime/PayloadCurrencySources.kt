@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.currency.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyFormatOptions
import dev.carcara.kotlinx.locale.currency.CurrencyFormatSource
import dev.carcara.kotlinx.locale.currency.CurrencyNameSource
import dev.carcara.kotlinx.locale.currency.forCodeOrNull
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.resolvedRecord
import dev.carcara.kotlinx.locale.internal.sparseRecordValue
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf
import dev.carcara.kotlinx.locale.number.NumberSymbols
import dev.carcara.kotlinx.locale.number.cldr.runtime.CompactPatternTable
import dev.carcara.kotlinx.locale.number.cldr.runtime.FormattedNumberSelector
import dev.carcara.kotlinx.locale.number.cldr.runtime.digitStringsOf

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
public class PayloadCurrencyFormats(
    private val formatRecords: Map<String, String>,
    nameRecords: Map<String, String>,
    /**
     * The compact money patterns, empty when this build did not ask for them.
     *
     * Empty is not a half-working state: compact notation then renders the
     * standard pattern, which is what CLDR's own `0` sentinel means for a
     * magnitude a locale has no compact form for.
     */
    private val compactRecords: Map<String, String> = emptyMap(),
    /**
     * How a divided value's plural category is chosen, which is step 8 of the
     * compact algorithm. Answers `other` when this build has no plural rules.
     */
    private val selectCategory: FormattedNumberSelector = NO_PLURAL_RULES,
) : CurrencyFormatSource {

    private val names = PayloadCurrencyNames(nameRecords)
    private val compactCache = HashMap<String, CompactPatternTable>()

    override val supportedLocales: Set<Locale> by lazy {
        supportedLocalesOf(formatRecords)
    }

    override fun formatOrNull(minorUnits: Long, currencyCode: String, locale: Locale, options: CurrencyFormatOptions): String? {
        val currency = Currency.forCodeOrNull(currencyCode) ?: return null
        val format = numberFormatFor(locale) ?: return null
        return formatCurrency(format, names, compactFor(locale), selectCategory, minorUnits, currency, locale, options)
    }

    private fun compactFor(locale: Locale): CompactPatternTable = compactCache.getOrPut(locale.toLanguageTag()) {
        CompactPatternTable(resolvedRecord(compactRecords, locale).orEmpty())
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

    /**
     * The same values as the number domain's symbol table, in the shape the
     * shared engine takes.
     *
     * The currency record carries its own copy rather than reaching into
     * `kotlinx-locale-number-cldr-full`, so a consumer who formats money does
     * not have to take the decimal, percent and compact tables to get a decimal
     * separator. It is a few kilobytes against a whole artifact.
     */
    public val symbols: NumberSymbols = NumberSymbols(
        numberingSystem = "latn",
        digits = digitStringsOf(digits),
        decimal = decimal,
        group = group,
        currencyDecimal = currencyDecimal,
        currencyGroup = currencyGroup,
        minusSign = minusSign,
        plusSign = "+",
        percentSign = "%",
        perMille = "‰",
        approximatelySign = "~",
        exponential = "E",
        superscriptingExponent = "×",
        infinity = "∞",
        nan = "NaN",
        listSeparator = ";",
        timeSeparator = ":",
        minimumGroupingDigits = minimumGroupingDigits,
    )

    public val standardPattern: String = fields[7]
    public val standardAlphaPattern: String = fields[8]
    public val accountingPattern: String = fields[9]
    public val accountingAlphaPattern: String = fields[10]
}

/** The number-format record for [locale], for the ICU cross-check. */
@InternalKotlinxLocaleApi
public fun currencyNumberFormatFor(records: Map<String, String>, locale: Locale): CurrencyNumberFormat =
    CurrencyNumberFormat(requireNotNull(resolvedRecord(records, locale)) { "no number format for $locale and no root" })
