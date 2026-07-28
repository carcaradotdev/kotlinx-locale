package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.internal.formatCurrency
import dev.carcara.kotlinx.locale.currency.internal.parseFormattedCurrency

/**
 * A monetary amount: a [currency] and a count of its ISO minor units — cents for
 * USD, fils for BHD (3 decimals), whole yen for JPY (0 decimals).
 *
 * `CurrencyAmount(Currency.USD, 1234_56)` is $1,234.56.
 */
public class CurrencyAmount(
    public val currency: Currency,
    /** The amount in ISO minor units, at [Currency.minorUnitDigits] fraction digits. */
    public val minorUnits: Long,
) : Comparable<CurrencyAmount> {

    private val scale: Long
        get() {
            var result = 1L
            repeat(currency.minorUnitDigits) { result *= 10 }
            return result
        }

    /** The whole-currency part, truncated toward zero: −1250 USD minor units → −12. */
    public val majorUnits: Long
        get() = minorUnits / scale

    /** The sub-unit remainder, carrying the amount's sign: −1250 USD minor units → −50. */
    public val minorPart: Int
        get() = (minorUnits % scale).toInt()

    /** Adds two amounts of the same currency. */
    public operator fun plus(other: CurrencyAmount): CurrencyAmount {
        requireSameCurrency(other)
        return CurrencyAmount(currency, minorUnits + other.minorUnits)
    }

    /** Subtracts an amount of the same currency. */
    public operator fun minus(other: CurrencyAmount): CurrencyAmount {
        requireSameCurrency(other)
        return CurrencyAmount(currency, minorUnits - other.minorUnits)
    }

    /** The negated amount. */
    public operator fun unaryMinus(): CurrencyAmount = CurrencyAmount(currency, -minorUnits)

    /** Compares amounts of the same currency. */
    override fun compareTo(other: CurrencyAmount): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    /**
     * Formats the amount with the CLDR pattern and symbols of [locale].
     *
     * The currency is written per [style]; [accounting] selects the accounting
     * pattern (e.g. `($1,234.56)` for negatives in en); [cash] applies CLDR's
     * cash fraction digits and cash rounding (e.g. CHF rounds to 0.05).
     * The number of fraction digits shown is CLDR's, which can differ from the
     * ISO minor units — the ISO→CLDR conversion rounds half-even.
     */
    public fun format(
        locale: Locale = Locale.current,
        style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL,
        accounting: Boolean = false,
        cash: Boolean = false,
    ): String = formatCurrency(minorUnits, currency, locale, style, accounting, cash)

    /**
     * The plain ISO decimal representation with `.` and ISO minor-unit digits:
     * −1250 USD minor units → `-12.50`; JPY 5 → `5`.
     */
    public fun toDecimalString(): String {
        val digits = currency.minorUnitDigits
        val magnitude = if (minorUnits < 0) 0uL - minorUnits.toULong() else minorUnits.toULong()
        val text = magnitude.toString()
        val integerLength = maxOf(text.length - digits, 0)
        var integerPart = text.substring(0, integerLength)
        var fractionPart = text.substring(integerLength)
        while (fractionPart.length < digits) fractionPart = "0" + fractionPart
        if (integerPart.isEmpty()) integerPart = "0"
        return buildString {
            if (minorUnits < 0) append('-')
            append(integerPart)
            if (digits > 0) {
                append('.')
                append(fractionPart)
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is CurrencyAmount && currency == other.currency && minorUnits == other.minorUnits

    override fun hashCode(): Int = 31 * currency.hashCode() + minorUnits.hashCode()

    /** `USD 12.50` — the ISO code and the ISO decimal value. */
    override fun toString(): String = "${currency.code} ${toDecimalString()}"

    private fun requireSameCurrency(other: CurrencyAmount) {
        require(currency == other.currency) {
            "Currency mismatch: ${currency.code} vs ${other.currency.code}"
        }
    }

    public companion object {

        /**
         * Builds an amount from major units and a signed sub-unit part:
         * `of(USD, 12, 50)` → 12.50, `of(USD, -12, -50)` → −12.50.
         *
         * @throws IllegalArgumentException when [minorPart] exceeds the currency's
         *   minor-unit range or its sign conflicts with [majorUnits].
         */
        public fun of(currency: Currency, majorUnits: Long, minorPart: Int = 0): CurrencyAmount {
            var scale = 1L
            repeat(currency.minorUnitDigits) { scale *= 10 }
            require(minorPart > -scale && minorPart < scale) {
                "minorPart $minorPart out of range for ${currency.code} (scale $scale)"
            }
            require(majorUnits == 0L || minorPart == 0 || (majorUnits < 0) == (minorPart < 0)) {
                "minorPart sign conflicts with majorUnits"
            }
            return CurrencyAmount(currency, majorUnits * scale + minorPart)
        }

        /**
         * Parses a plain ISO decimal string — an optional `-`, digits, and at most
         * [Currency.minorUnitDigits] fraction digits after `.` — into an amount.
         * `parseOrNull(USD, "12.5")` → 12.50. Returns `null` on malformed input,
         * excess fraction digits, or overflow.
         */
        public fun parseOrNull(currency: Currency, text: String): CurrencyAmount? {
            val digits = currency.minorUnitDigits
            var rest = text
            val negative = rest.startsWith('-')
            if (negative) rest = rest.substring(1)
            if (rest.isEmpty()) return null

            val dot = rest.indexOf('.')
            val integerPart = if (dot < 0) rest else rest.substring(0, dot)
            val fractionPart = if (dot < 0) "" else rest.substring(dot + 1)
            if (integerPart.isEmpty() && fractionPart.isEmpty()) return null
            if (dot >= 0 && fractionPart.isEmpty()) return null
            if (fractionPart.length > digits) return null
            if (!integerPart.all { it in '0'..'9' } || !fractionPart.all { it in '0'..'9' }) return null

            // Apply the sign before converting so Long.MIN_VALUE round trips.
            val combined = integerPart + fractionPart.padEnd(digits, '0')
            val signed = (if (negative) "-" else "") + combined
            return CurrencyAmount(currency, signed.toLongOrNull() ?: return null)
        }

        /** Like [parseOrNull] but throws on invalid input. */
        public fun parse(currency: Currency, text: String): CurrencyAmount =
            requireNotNull(parseOrNull(currency, text)) {
                "Cannot parse ${currency.code} amount: '$text'"
            }

        /**
         * Parses a CLDR-formatted string — `R$ 1.234,56`, `($1,234.56)`,
         * `200 Ft` — back into an amount, using [locale]'s separators, digits
         * and currency symbol.
         *
         * The printed number is taken at face value and scaled to ISO minor
         * units, so CLDR's formatting digits do not distort the result: HUF
         * formats with no decimals but has two ISO decimals, and `"200 Ft"`
         * parses to 20000 minor units. The currency may appear as its
         * localized symbol, ISO code or display name, or be absent entirely.
         * Negatives are recognized from the locale's minus sign or accounting
         * parentheses. Returns `null` when the text has content other than
         * one number with this locale's separators, or when the fraction
         * cannot be represented in ISO minor units.
         */
        public fun parseFormattedOrNull(
            currency: Currency,
            text: String,
            locale: Locale = Locale.current,
        ): CurrencyAmount? =
            parseFormattedCurrency(text, currency, locale)?.let { CurrencyAmount(currency, it) }

        /** Like [parseFormattedOrNull] but throws on invalid input. */
        public fun parseFormatted(
            currency: Currency,
            text: String,
            locale: Locale = Locale.current,
        ): CurrencyAmount =
            requireNotNull(parseFormattedOrNull(currency, text, locale)) {
                "Cannot parse ${currency.code} amount: '$text'"
            }
    }
}
