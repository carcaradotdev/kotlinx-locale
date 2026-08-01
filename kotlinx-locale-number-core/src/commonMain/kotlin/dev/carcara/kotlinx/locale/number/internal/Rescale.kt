package dev.carcara.kotlinx.locale.number.internal

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

/**
 * Rescales [value] from [fromDigits] fraction digits to [toDigits], multiplying
 * when gaining digits and dividing half-even when losing them.
 *
 * Shared with the implementation modules, which have to move an amount onto
 * whatever scale they format at. There is one rounding rule and it lives here.
 *
 * Half-even rather than half-up because that is what ICU's number formatter
 * defaults to and what LDML's own examples show; LDML itself does not say.
 */
@InternalKotlinxLocaleApi
public fun rescaleFraction(value: Long, fromDigits: Int, toDigits: Int): Long {
    if (fromDigits == toDigits) return value
    if (toDigits > fromDigits) {
        var result = value
        repeat(toDigits - fromDigits) { result *= 10 }
        return result
    }
    var divisor = 1L
    repeat(fromDigits - toDigits) { divisor *= 10 }
    return divideHalfEven(value, divisor)
}

/** Rounds [value] to the nearest multiple of [increment], ties to the even multiple. */
@InternalKotlinxLocaleApi
public fun roundToIncrement(value: Long, increment: Long): Long = divideHalfEven(value, increment) * increment

/**
 * Rounds [value], read at [digits] fraction digits, to [significantDigits]
 * significant digits, keeping its scale.
 *
 * Compact notation is the caller: its default precision is expressed in
 * significant digits, so 1234 has to become 1200 before the magnitude is
 * divided out.
 */
@InternalKotlinxLocaleApi
public fun roundToSignificantDigits(value: Long, digits: Int, significantDigits: Int): Long {
    require(significantDigits > 0) { "significantDigits must be positive, was $significantDigits" }
    if (value == 0L) return 0L
    var magnitude = 0
    var remaining = if (value < 0) -value else value
    while (remaining >= 10) {
        remaining /= 10
        magnitude++
    }
    val drop = magnitude + 1 - significantDigits
    if (drop <= 0) return value
    // Rescale down and straight back up, so the scale is unchanged and only the
    // digits below the significant ones are lost.
    return rescaleFraction(rescaleFraction(value, digits, digits - drop), digits - drop, digits)
}

private fun divideHalfEven(value: Long, divisor: Long): Long {
    val quotient = value / divisor
    val remainder = value % divisor
    if (remainder == 0L) return quotient
    val distance = if (remainder < 0) -remainder else remainder
    // Compare against divisor/2 without overflow: distance vs divisor - distance.
    val comparison = distance.compareTo(divisor - distance)
    val roundAway = comparison > 0 || (comparison == 0 && quotient % 2 != 0L)
    if (!roundAway) return quotient
    return if (value < 0) quotient - 1 else quotient + 1
}
