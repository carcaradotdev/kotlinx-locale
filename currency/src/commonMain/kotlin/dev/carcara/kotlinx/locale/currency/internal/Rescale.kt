package dev.carcara.kotlinx.locale.currency.internal

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

/**
 * Rescales [value] from [fromDigits] fraction digits to [toDigits], multiplying
 * when gaining digits and dividing half-even when losing them.
 *
 * Shared with the implementation modules, which have to move an ISO minor-unit
 * amount onto whatever scale they format at. There is one rounding rule and it
 * lives here.
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
