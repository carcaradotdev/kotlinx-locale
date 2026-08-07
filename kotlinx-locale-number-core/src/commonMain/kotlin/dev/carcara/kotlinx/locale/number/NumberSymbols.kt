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
 * A locale's number symbols, as CLDR declares them for its default numbering
 * system.
 *
 * Handed out whole so a caller can build something this library does not format.
 * An amount field that formats while someone types has to preserve states a
 * formatter would normalise away — a trailing `5.`, a typed `1.50` that must not
 * collapse to `1.5` — so it cannot round trip through `format`. What it needs is
 * [decimal] and [group] to filter keystrokes and [digits] to echo them, and no
 * `format` call would give it those.
 *
 * ICU exposes the same table as `DecimalFormatSymbols`. The values are CLDR's
 * verbatim, including the no-break spaces several locales use for [group].
 */
public class NumberSymbols(
    /** The numbering system id, e.g. `latn`, `arab`, `deva`. */
    public val numberingSystem: String,
    /** The ten digits, one string each, so supplementary-plane systems work. */
    public val digits: List<String>,
    public val decimal: String,
    public val group: String,
    /** Used instead of [decimal] inside currency amounts; equal to it in most locales. */
    public val currencyDecimal: String,
    public val currencyGroup: String,
    public val minusSign: String,
    public val plusSign: String,
    public val percentSign: String,
    public val perMille: String,
    public val approximatelySign: String,
    public val exponential: String,
    public val superscriptingExponent: String,
    public val infinity: String,
    public val nan: String,
    /** The non-linguistic list separator, `;` in en. Not the `and` of a list pattern. */
    public val listSeparator: String,
    public val timeSeparator: String,
    /**
     * How many integer digits a number needs before the first group separator
     * appears.
     *
     * 1 in most locales. 2 in Polish, Spanish and a handful of others, where
     * 1000 prints as `1000` and 10000 as `10 000`.
     */
    public val minimumGroupingDigits: Int,
) {

    init {
        require(digits.size == 10) { "a numbering system has ten digits, not ${digits.size}" }
        require(minimumGroupingDigits >= 1) { "minimumGroupingDigits is at least 1, was $minimumGroupingDigits" }
    }

    override fun toString(): String = "NumberSymbols($numberingSystem, decimal='$decimal', group='$group')"

    public companion object {

        /**
         * CLDR root's symbols, which is what a source with nothing for a locale
         * falls back to.
         *
         * Latin digits, a full stop for the decimal separator and a comma for
         * grouping, which is root's answer and not a guess.
         */
        public val Root: NumberSymbols = NumberSymbols(
            numberingSystem = "latn",
            digits = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"),
            decimal = ".",
            group = ",",
            currencyDecimal = ".",
            currencyGroup = ",",
            minusSign = "-",
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
            minimumGroupingDigits = 1,
        )
    }
}
