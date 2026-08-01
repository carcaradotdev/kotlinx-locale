@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.number.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.internal.ENTRY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.resolvedRecord
import dev.carcara.kotlinx.locale.number.NumberSymbols
import dev.carcara.kotlinx.locale.number.PluralCategory

/**
 * Decoded number symbols for one locale, fully resolved at generation time.
 *
 * Public under the internal-API marker so the ICU cross-check can compare the
 * tables directly. Consumers reach the same values through
 * [dev.carcara.kotlinx.locale.number.NumberSymbols], which is the shape without
 * the record.
 */
@InternalKotlinxLocaleApi
public class NumberSymbolsRecord(record: String) {

    private val fields = record.split(FIELD_SEPARATOR)

    public val numberingSystem: String = fields[0]

    /** The ten digits of the locale's default numbering system, as one string. */
    public val digits: String = fields[1]
    public val decimal: String = fields[2]
    public val group: String = fields[3]
    public val currencyDecimal: String = fields[4]
    public val currencyGroup: String = fields[5]
    public val minusSign: String = fields[6]
    public val plusSign: String = fields[7]
    public val percentSign: String = fields[8]
    public val perMille: String = fields[9]
    public val approximatelySign: String = fields[10]
    public val exponential: String = fields[11]
    public val superscriptingExponent: String = fields[12]
    public val infinity: String = fields[13]
    public val nan: String = fields[14]
    public val listSeparator: String = fields[15]
    public val timeSeparator: String = fields[16]
    public val minimumGroupingDigits: Int = fields[17].toIntOrNull() ?: 1

    public fun toSymbols(): NumberSymbols = NumberSymbols(
        numberingSystem = numberingSystem,
        digits = digitStringsOf(digits),
        decimal = decimal,
        group = group,
        currencyDecimal = currencyDecimal,
        currencyGroup = currencyGroup,
        minusSign = minusSign,
        plusSign = plusSign,
        percentSign = percentSign,
        perMille = perMille,
        approximatelySign = approximatelySign,
        exponential = exponential,
        superscriptingExponent = superscriptingExponent,
        infinity = infinity,
        nan = nan,
        listSeparator = listSeparator,
        timeSeparator = timeSeparator,
        minimumGroupingDigits = minimumGroupingDigits,
    )
}

/** The plain decimal and percent patterns for one locale. */
@InternalKotlinxLocaleApi
public class NumberPatternRecord(record: String) {

    private val fields = record.split(FIELD_SEPARATOR)

    public val decimalPattern: String = fields[0]
    public val percentPattern: String = fields[1]
}

/** The symbol record for [locale], for the ICU cross-check. */
@InternalKotlinxLocaleApi
public fun numberSymbolsRecordFor(records: Map<String, String>, locale: Locale): NumberSymbolsRecord =
    NumberSymbolsRecord(requireNotNull(resolvedRecord(records, locale)) { "no number symbols for $locale and no root" })

/**
 * One locale's compact patterns for one length, keyed by magnitude and plural
 * category.
 *
 * The encoded form is `magnitude:category:alt=pattern` entries. A pattern whose
 * text is exactly `0` is a value rather than an absence: UTS #35 uses it to mean
 * "fall back to the standard pattern at this magnitude", and ten locales set it
 * deliberately to override a parent that had a real pattern. Resolution must not
 * skip past it.
 */
@InternalKotlinxLocaleApi
public class CompactPatternTable(record: String) {

    private val patterns = HashMap<String, String>()

    /** The largest power of ten the table declares; above it the largest pattern is reused. */
    public val maximumMagnitude: Int

    init {
        var largest = 0
        if (record.isNotEmpty()) {
            for (entry in record.split(ENTRY_SEPARATOR)) {
                if (entry.isEmpty()) continue
                val equals = entry.indexOf('=')
                if (equals <= 0) continue
                val key = entry.substring(0, equals)
                patterns[key] = entry.substring(equals + 1)
                val magnitude = key.substringBefore(':').toIntOrNull() ?: continue
                if (magnitude > largest) largest = magnitude
            }
        }
        maximumMagnitude = largest
    }

    public val isEmpty: Boolean get() = patterns.isEmpty()

    /**
     * The pattern for 10^[magnitude] in [category], or `null` when the table
     * declares none.
     *
     * Falls back from the alphaNextToNumber variant to the plain one, and from
     * the asked-for category to `other`, which is what CLDR's own inheritance
     * does within a compact block.
     */
    public fun patternOrNull(magnitude: Int, category: PluralCategory, alphaNextToNumber: Boolean): String? {
        if (alphaNextToNumber) {
            patterns["$magnitude:${category.cldrName}:a"]?.let { return it }
            patterns["$magnitude:other:a"]?.let { return it }
        }
        patterns["$magnitude:${category.cldrName}"]?.let { return it }
        return patterns["$magnitude:other"]
    }
}
