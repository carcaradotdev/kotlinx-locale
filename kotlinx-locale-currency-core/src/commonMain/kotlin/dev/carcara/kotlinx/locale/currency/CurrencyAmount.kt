/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.carcara.kotlinx.locale.currency

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

    override fun equals(other: Any?): Boolean = other is CurrencyAmount && currency == other.currency && minorUnits == other.minorUnits

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
        public fun parse(currency: Currency, text: String): CurrencyAmount = requireNotNull(parseOrNull(currency, text)) {
            "Cannot parse ${currency.code} amount: '$text'"
        }
    }
}
