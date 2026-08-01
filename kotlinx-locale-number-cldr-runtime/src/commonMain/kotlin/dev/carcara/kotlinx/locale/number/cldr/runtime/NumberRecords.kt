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
    public fun patternOrNull(
        magnitude: Int,
        category: PluralCategory,
        alphaNextToNumber: Boolean,
        /**
         * The divided value when it is a whole number, for CLDR's explicit
         * count keys.
         *
         * A `count` is usually a plural category, but it may also be a literal
         * integer, and the literal wins when the value matches. French declares
         * `1000-count-1` as `mille`, which is why one thousand reads `mille`
         * rather than `1 millier`, and there is no plural category that could
         * express the difference.
         */
        exactValue: Long? = null,
    ): String? {
        if (exactValue != null) {
            if (alphaNextToNumber) patterns["$magnitude:$exactValue:a"]?.let { return it }
            patterns["$magnitude:$exactValue"]?.let { return it }
        }
        if (alphaNextToNumber) {
            patterns["$magnitude:${category.cldrName}:a"]?.let { return it }
            patterns["$magnitude:other:a"]?.let { return it }
        }
        patterns["$magnitude:${category.cldrName}"]?.let { return it }
        return patterns["$magnitude:other"]
    }

    /**
     * Whether this locale writes a compact form at all at [magnitude].
     *
     * False for the `"0"` sentinel, which ten locales use to override a
     * parent's entry back to the full number. Asked before the divide rather
     * than after, because rounding 9999 up would otherwise carry it into the
     * next entry and produce a compact form the locale declined to declare.
     */
    public fun hasCompactForm(magnitude: Int): Boolean {
        val pattern = patterns["$magnitude:other"] ?: return false
        return pattern != "0"
    }

    /**
     * The power of ten to divide by before rendering at [magnitude].
     *
     * Read off the pattern rather than assumed, because the entries are not
     * grouped in threes everywhere. A pattern's zeros say how many integer
     * digits survive the divide, so English `000K` at 10^5 keeps three and
     * divides by 10^3, while Bengali `0 লা` at the same magnitude keeps one and
     * divides by 10^5. Hard-coding the Western grouping writes 99999 as
     * `100 লা` where the answer is `1 লা`.
     */
    public fun divisorExponent(magnitude: Int): Int {
        val pattern = patterns["$magnitude:other"] ?: return magnitude - (magnitude % 3)
        var zeros = 0
        var inQuote = false
        for (ch in pattern) {
            when {
                ch == '\'' -> inQuote = !inQuote
                inQuote -> Unit
                // The negative subpattern repeats the zeros, so counting past
                // the separator doubles them. Swahili writes its thousands as
                // `elfu 0;elfu -0`, which is one digit and not two.
                ch == ';' -> return magnitude - (zeros - 1).coerceAtLeast(0)
                ch == '0' -> zeros++
            }
        }
        if (zeros == 0) return magnitude - (magnitude % 3)
        return magnitude - (zeros - 1)
    }
}
