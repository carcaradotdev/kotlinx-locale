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

package dev.carcara.kotlinx.locale.number

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

/**
 * A number as it was printed, together with the UTS #35 plural operands read off
 * it.
 *
 * The operands are why this type exists. Czech's cardinal rules are
 * `one: i = 1 and v = 0` and `many: v != 0`, where `v` counts the *visible*
 * fraction digits, so `1` is `one` and `1.0` is `many` for the same numeric
 * value. A category taken from a raw number cannot answer that; it can only
 * answer for integers, where `v` is zero by construction. ICU has the same type
 * for the same reason and calls the pairing `PluralRules.select(FormattedNumber)`.
 *
 * Implements [CharSequence], so it drops into a `StringBuilder` or a string
 * template without anyone reaching for [text].
 */
public class FormattedNumber @InternalKotlinxLocaleApi public constructor(
    /** The formatted text, with the locale's own digits, separators and affixes. */
    public val text: String,
    /** ASCII integer digits of the printed value: no sign, no grouping, no locale digits. */
    integerDigits: String,
    /** ASCII fraction digits as printed, trailing zeros included. */
    fractionDigits: String,
    /** The compact exponent, UTS #35 operand `c`; 0 in standard notation. */
    compactExponent: Int = 0,
) : CharSequence {

    /** Operand `i`: the integer part, without a sign. */
    public val i: Long = integerDigits.trimStart('0').ifEmpty { "0" }.toLongOrNull() ?: Long.MAX_VALUE

    /** Operand `v`: the count of visible fraction digits, trailing zeros included. */
    public val v: Int = fractionDigits.length

    /** Operand `w`: the count of visible fraction digits, trailing zeros excluded. */
    public val w: Int = fractionDigits.trimEnd('0').length

    /** Operand `f`: the visible fraction digits as an integer, trailing zeros included. */
    public val f: Long = fractionDigits.toLongOrNull() ?: 0L

    /** Operand `t`: the visible fraction digits as an integer, trailing zeros excluded. */
    public val t: Long = fractionDigits.trimEnd('0').toLongOrNull() ?: 0L

    /** Operand `c`: the compact decimal exponent, the power of ten already divided out. */
    public val c: Int = compactExponent

    /** Operand `e`: CLDR's older name for [c]. The spec keeps both and they are the same value. */
    public val e: Int get() = c

    /**
     * Operand `n`: the absolute value as printed.
     *
     * A [Decimal] rather than a `Double`, because the rules compare it against
     * integers and ranges and a float would make `n = 1` depend on rounding.
     */
    public val n: Decimal = Decimal.parseOrNull(
        if (fractionDigits.isEmpty()) integerDigits.ifEmpty { "0" } else "${integerDigits.ifEmpty { "0" }}.$fractionDigits",
    ) ?: Decimal.ZERO

    override val length: Int get() = text.length

    override fun get(index: Int): Char = text[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)

    override fun toString(): String = text
}
