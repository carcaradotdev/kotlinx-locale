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

/**
 * When a sign is written, and whether the accounting pattern is used.
 *
 * The two questions are one enum because CLDR ties them: what makes
 * `($1,234.56)` accounting is the negative subpattern of
 * `currencyFormat type="accounting"`, so "use parentheses" and "show a minus"
 * are the same choice made twice. This is ICU's `SignDisplay`, which absorbed a
 * separate accounting flag for the same reason.
 *
 * UTS #35 has an "Explicit Plus Signs" rule for [ALWAYS] and its accounting
 * variant: the positive subpattern is derived from the negative one by replacing
 * the minus with a plus, so a locale that writes its negatives as `1,0 −` writes
 * its explicit positives as `1,0 +` rather than prefixing.
 */
public enum class SignDisplay {

    /** A minus on negatives, nothing on zero or positives. The default everywhere. */
    AUTO,

    /** A sign on everything, including zero. */
    ALWAYS,

    /** No sign at all, on any value. */
    NEVER,

    /** A sign on everything except zero, which is what a transaction list wants. */
    EXCEPT_ZERO,

    /**
     * Like [AUTO] but never on a value that rounded to zero from below.
     *
     * [Decimal] holds no negative zero, so this is about rounding rather than
     * about the input: -0.5 at no fraction digits is `-0` under [AUTO] and `0`
     * here. Both reference implementations keep the sign by default, on the
     * grounds that a reading of -0.4°C shown to the nearest degree is still
     * below freezing, so this is the value to reach for when it is not.
     */
    NEGATIVE,

    /** The locale's accounting form on negatives, nothing on positives. */
    ACCOUNTING,

    /** The accounting form on negatives and a plus on positives, including zero. */
    ACCOUNTING_ALWAYS,

    /** The accounting form on negatives and a plus on positives, except zero. */
    ACCOUNTING_EXCEPT_ZERO,

    /** The accounting form on negatives, never on negative zero. */
    ACCOUNTING_NEGATIVE,
    ;

    /** True for the four accounting variants, which select CLDR's accounting pattern. */
    public val usesAccountingPattern: Boolean
        get() = this == ACCOUNTING ||
            this == ACCOUNTING_ALWAYS ||
            this == ACCOUNTING_EXCEPT_ZERO ||
            this == ACCOUNTING_NEGATIVE

    /** True when a positive value should carry an explicit plus. */
    public val showsPlus: Boolean
        get() = this == ALWAYS || this == EXCEPT_ZERO || this == ACCOUNTING_ALWAYS || this == ACCOUNTING_EXCEPT_ZERO

    /** True when zero should carry a sign, which only the two "always" forms ask for. */
    public val signsZero: Boolean
        get() = this == ALWAYS || this == ACCOUNTING_ALWAYS

    /**
     * True when a negative value that rounded to zero loses its sign.
     *
     * The default is to keep it: -0.5 at no fraction digits is `-0`, which is
     * what ICU and `Intl.NumberFormat` both write. The four values here are the
     * ones that ask for `0` instead, either by naming negative zero directly or
     * by declining to sign anything that rounds to zero.
     */
    public val suppressesNegativeZero: Boolean
        get() = this == NEGATIVE ||
            this == EXCEPT_ZERO ||
            this == ACCOUNTING_NEGATIVE ||
            this == ACCOUNTING_EXCEPT_ZERO
}

/**
 * Standard notation, or CLDR's short and long compact forms.
 *
 * `12345` is `12,345` standard, `12K` short and `12 thousand` long. The tables
 * behind the two compact forms are `decimalFormatLength type="short"` and
 * `type="long"`, and for money `currencyFormatLength type="short"`.
 */
public enum class NumberNotation { STANDARD, COMPACT_SHORT, COMPACT_LONG }

/**
 * Whether grouping separators appear.
 *
 * [AUTO] honours the locale's `minimumGroupingDigits`, which is why `1000` is
 * `1000` in Polish and Spanish but `1,000` in English, while `10000` groups in
 * all three. [ALWAYS] ignores that and groups from the first opportunity;
 * [NEVER] writes no separators at all.
 */
public enum class NumberGrouping { AUTO, ALWAYS, NEVER }

/**
 * Whether a percent value arrives as a fraction or already scaled.
 *
 * UTS #35 is explicit that a `%` in a pattern multiplies by 100 ("That way
 * 1.23 → 123%"), and that is what this library's engine does, matching
 * `Intl.NumberFormat` and `java.text.NumberFormat.getPercentInstance`. ICU's
 * newer `NumberFormatter.unit(NoUnit.PERCENT)` does not multiply.
 *
 * Both readings have standing, and guessing wrong is a silently hundredfold
 * wrong number, so the scale is named rather than assumed. The two entry points
 * are named for what they take.
 */
public enum class PercentScale {

    /** `0.075` prints as `7.5%`. The UTS #35 reading. */
    FRACTION,

    /** `7.5` prints as `7.5%`. */
    PERCENT,
}

/**
 * Everything a number format can be asked for beyond the value and the locale.
 *
 * A class rather than parameters on [NumberFormatSource.formatOrNull] because
 * the interface is implemented outside this build. Every option added here is a
 * field; every option added to the method is a breaking change for every
 * implementor and every fallback composer. The public extensions still take flat
 * named parameters, which is what a consumer types.
 */
public class NumberFormatOptions(
    public val notation: NumberNotation = NumberNotation.STANDARD,
    public val signDisplay: SignDisplay = SignDisplay.AUTO,
    public val grouping: NumberGrouping = NumberGrouping.AUTO,
    /** `null` takes the pattern's own minimum. */
    public val minimumFractionDigits: Int? = null,
    /** `null` takes the pattern's own maximum, or the compact default. */
    public val maximumFractionDigits: Int? = null,
    public val minimumIntegerDigits: Int = 1,
) {

    init {
        require(minimumIntegerDigits >= 0) { "minimumIntegerDigits cannot be negative" }
        require(minimumFractionDigits == null || minimumFractionDigits >= 0) {
            "minimumFractionDigits cannot be negative"
        }
        require(maximumFractionDigits == null || maximumFractionDigits >= 0) {
            "maximumFractionDigits cannot be negative"
        }
        if (minimumFractionDigits != null && maximumFractionDigits != null) {
            require(minimumFractionDigits <= maximumFractionDigits) {
                "minimumFractionDigits $minimumFractionDigits exceeds maximumFractionDigits $maximumFractionDigits"
            }
        }
    }

    public companion object {
        public val Default: NumberFormatOptions = NumberFormatOptions()
    }
}
