@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.number

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.number.internal.rescaleFraction

/** The largest scale a [Long] of unscaled units can carry without losing the integer part entirely. */
private const val MAX_SCALE = 18

/**
 * An exact decimal: [unscaled] units at [scale] fraction digits, so
 * `Decimal.ofUnscaled(12345, 2)` is `123.45`.
 *
 * This is the type every formatter here takes, and the reason is plural rules.
 * UTS #35's operands `v`, `w`, `f` and `t` count the digits a number is *printed
 * with*, so `1` and `1.0` fall into different categories in Czech for the same
 * numeric value. A binary float cannot say how many digits it has, and the
 * platforms do not agree on what `Double.toString` produces, so a formatter that
 * inferred the precision would print different text on different targets for the
 * same input.
 *
 * It is `CurrencyAmount` generalised away from a currency, and it holds the same
 * range: 18 significant digits of unscaled units, with [scale] between 0 and 18.
 * There is no arbitrary precision here and none is planned; a locale library
 * formats what it is handed.
 */
public class Decimal private constructor(public val unscaled: Long, public val scale: Int) : Comparable<Decimal> {

    public val isZero: Boolean get() = unscaled == 0L

    public val isNegative: Boolean get() = unscaled < 0L

    /** The integer part, truncated towards zero: `-1.7` is `-1`. */
    public val integerPart: Long
        get() {
            var divisor = 1L
            repeat(scale) { divisor *= 10 }
            return unscaled / divisor
        }

    /** The same value at [scale] fraction digits, rounding half-even when losing digits. */
    public fun rescaled(scale: Int): Decimal {
        require(scale in 0..MAX_SCALE) { "scale must be in 0..$MAX_SCALE, was $scale" }
        if (scale == this.scale) return this
        return Decimal(rescaleFraction(unscaled, this.scale, scale), scale)
    }

    public operator fun unaryMinus(): Decimal = Decimal(-unscaled, scale)

    public val absoluteValue: Decimal get() = if (unscaled < 0) Decimal(-unscaled, scale) else this

    /**
     * `-12.50`: an optional minus, the integer digits, and exactly [scale]
     * fraction digits after a `.`.
     *
     * ASCII throughout and locale-independent, which is what makes it a
     * debugging and wire form rather than something to show anyone.
     */
    public fun toPlainString(): String = buildString {
        if (unscaled < 0) append('-')
        val digits = absoluteDigits()
        if (scale == 0) {
            append(digits)
            return@buildString
        }
        val padded = digits.padStart(scale + 1, '0')
        append(padded, 0, padded.length - scale)
        append('.')
        append(padded, padded.length - scale, padded.length)
    }

    /** The unsigned digits of [unscaled], with no separator and no sign. */
    @InternalKotlinxLocaleApi
    public fun absoluteDigits(): String = if (unscaled == Long.MIN_VALUE) {
        unscaled.toString().substring(1)
    } else {
        (if (unscaled < 0) -unscaled else unscaled).toString()
    }

    override fun compareTo(other: Decimal): Int {
        if (scale == other.scale) return unscaled.compareTo(other.unscaled)
        val common = maxOf(scale, other.scale)
        return rescaled(common).unscaled.compareTo(other.rescaled(common).unscaled)
    }

    /**
     * Equal when the unscaled value and the scale both match, so `1.0` does not
     * equal `1`.
     *
     * Deliberate, and the same choice `BigDecimal` makes: the scale is part of
     * the value here because it decides the plural category and the printed
     * text. Use [compareTo] to compare numerically.
     */
    override fun equals(other: Any?): Boolean = other is Decimal && unscaled == other.unscaled && scale == other.scale

    override fun hashCode(): Int = unscaled.hashCode() * 31 + scale

    override fun toString(): String = toPlainString()

    public companion object {

        public val ZERO: Decimal = Decimal(0L, 0)

        public fun of(value: Long): Decimal = Decimal(value, 0)

        public fun ofUnscaled(unscaled: Long, scale: Int): Decimal {
            require(scale in 0..MAX_SCALE) { "scale must be in 0..$MAX_SCALE, was $scale" }
            return Decimal(unscaled, scale)
        }

        /**
         * [value] rounded half-even to [fractionDigits], or `null` when the
         * result does not fit a [Long] of unscaled units.
         *
         * The digit count is required rather than derived. `Double.toString`
         * does not agree across Kotlin/JVM, Kotlin/JS and Kotlin/Native, so a
         * formatter that read the precision off the float would print different
         * text on different targets for one input, which is the class of bug
         * this library exists to remove.
         */
        public fun ofOrNull(value: Double, fractionDigits: Int): Decimal? {
            if (fractionDigits !in 0..MAX_SCALE) return null
            if (value.isNaN() || value.isInfinite()) return null
            var factor = 1.0
            repeat(fractionDigits) { factor *= 10.0 }
            val scaled = value * factor
            if (scaled >= 9.223372036854776E18 || scaled <= -9.223372036854776E18) return null
            return Decimal(roundHalfEven(scaled), fractionDigits)
        }

        /** Like [ofOrNull] but throws when the value does not fit. */
        public fun of(value: Double, fractionDigits: Int): Decimal = requireNotNull(ofOrNull(value, fractionDigits)) {
            "$value does not fit a Decimal at $fractionDigits fraction digits"
        }

        /** `-12.50` read back, or `null` when [text] is not that shape. */
        public fun parseOrNull(text: String): Decimal? {
            if (text.isEmpty()) return null
            val negative = text[0] == '-'
            val body = if (negative || text[0] == '+') text.substring(1) else text
            if (body.isEmpty()) return null
            val point = body.indexOf('.')
            val digits = if (point < 0) body else body.substring(0, point) + body.substring(point + 1)
            if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
            val scale = if (point < 0) 0 else body.length - point - 1
            if (scale > MAX_SCALE) return null
            val unscaled = digits.toLongOrNull() ?: return null
            return Decimal(if (negative) -unscaled else unscaled, scale)
        }

        /** Like [parseOrNull] but throws on anything it cannot read. */
        public fun parse(text: String): Decimal = requireNotNull(parseOrNull(text)) { "not a decimal: '$text'" }

        private fun roundHalfEven(value: Double): Long {
            val floor = kotlin.math.floor(value)
            val fraction = value - floor
            val rounded = when {
                fraction > 0.5 -> floor + 1
                fraction < 0.5 -> floor
                // Exactly halfway: take whichever neighbour is even.
                floor.toLong() % 2 == 0L -> floor
                else -> floor + 1
            }
            return rounded.toLong()
        }
    }
}
