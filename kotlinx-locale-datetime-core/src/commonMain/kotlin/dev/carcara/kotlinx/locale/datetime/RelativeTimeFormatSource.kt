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

package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource

/** The units CLDR carries relative names for. */
public enum class RelativeTimeUnit {
    YEAR,
    QUARTER,
    MONTH,
    WEEK,
    DAY,
    HOUR,
    MINUTE,
    SECOND,
    ;

    public companion object
}

/** How wide the wording is: CLDR's base field and its short and narrow variants. */
public enum class RelativeTimeStyle {
    FULL,
    SHORT,
    NARROW,
    ;

    public companion object
}

/**
 * Whether the wording always counts, or prefers a word where the locale has one.
 *
 * [AUTO] gives `yesterday` for minus one day and `3 days ago` for minus three;
 * [ALWAYS] gives `1 day ago` and `3 days ago`. The same distinction ECMA-402
 * spells `numeric`.
 */
public enum class RelativeTimeNumbering {
    AUTO,
    ALWAYS,
    ;

    public companion object
}

/**
 * A source that writes an offset in time the way a locale writes it.
 *
 * The caller supplies both the value and the unit. Choosing the unit — whether
 * ninety minutes reads as `in 90 minutes` or `in 2 hours` — is deliberately not
 * part of this contract, because no standard defines it: CLDR carries the
 * wording for a given value and unit and says nothing about when to switch
 * units, and neither `Intl.RelativeTimeFormat` nor ICU's
 * `RelativeDateTimeFormatter` decides it for you. A ladder is a product
 * decision, since a chat app and a changelog want different thresholds, so this
 * library does not make one.
 */
public interface RelativeTimeFormatSource : LocaleDataSource {

    /**
     * [value] [unit]s from now, written for [locale]; negative is the past.
     *
     * The plural form is selected from the number as it will be printed, which
     * is what makes Czech `1 den` and `1,0 dne` differ.
     */
    public fun formatOrNull(
        value: Long,
        unit: RelativeTimeUnit,
        style: RelativeTimeStyle,
        numbering: RelativeTimeNumbering,
        locale: Locale,
    ): String?

    /**
     * The word CLDR has for exactly [offset] [unit]s — `yesterday`, `zítra`,
     * `předevčírem` — or `null` when the locale has none.
     *
     * CLDR carries only minus two through two, and only some units and locales
     * use the full range.
     */
    public fun literalOrNull(offset: Int, unit: RelativeTimeUnit, style: RelativeTimeStyle, locale: Locale): String?

    /** The locale's name for [unit] itself: `month`, `měsíc`. */
    public fun unitNameOrNull(unit: RelativeTimeUnit, style: RelativeTimeStyle, locale: Locale): String?

    public companion object
}

/**
 * [value] [unit]s from now, written for [locale].
 *
 * Falls back to a bare English `in 3 days` or `3 days ago` when this build has
 * nothing for the locale, so the call is total.
 */
public fun RelativeTimeFormatSource.format(
    value: Long,
    unit: RelativeTimeUnit,
    style: RelativeTimeStyle = RelativeTimeStyle.FULL,
    numbering: RelativeTimeNumbering = RelativeTimeNumbering.AUTO,
    locale: Locale = Locale.current,
): String = formatOrNull(value, unit, style, numbering, locale) ?: fallbackWording(value, unit)

private fun fallbackWording(value: Long, unit: RelativeTimeUnit): String {
    val name = unit.name.lowercase() + if (value == 1L || value == -1L) "" else "s"
    return if (value < 0) "${-value} $name ago" else "in $value $name"
}

/** The locale's name for [unit]; falls back to the English enum name. */
public fun RelativeTimeFormatSource.unitName(
    unit: RelativeTimeUnit,
    style: RelativeTimeStyle = RelativeTimeStyle.FULL,
    locale: Locale = Locale.current,
): String = unitNameOrNull(unit, style, locale) ?: unit.name.lowercase()

/** Answers from [primary], and from [fallback] wherever primary has nothing. */
public class FallbackRelativeTimeFormats(private val primary: RelativeTimeFormatSource, private val fallback: RelativeTimeFormatSource) :
    RelativeTimeFormatSource {

    override val supportedLocales: Set<Locale> get() = primary.supportedLocales + fallback.supportedLocales

    override fun formatOrNull(
        value: Long,
        unit: RelativeTimeUnit,
        style: RelativeTimeStyle,
        numbering: RelativeTimeNumbering,
        locale: Locale,
    ): String? = primary.formatOrNull(value, unit, style, numbering, locale)
        ?: fallback.formatOrNull(value, unit, style, numbering, locale)

    override fun literalOrNull(offset: Int, unit: RelativeTimeUnit, style: RelativeTimeStyle, locale: Locale): String? =
        primary.literalOrNull(offset, unit, style, locale) ?: fallback.literalOrNull(offset, unit, style, locale)

    override fun unitNameOrNull(unit: RelativeTimeUnit, style: RelativeTimeStyle, locale: Locale): String? =
        primary.unitNameOrNull(unit, style, locale) ?: fallback.unitNameOrNull(unit, style, locale)

    public companion object
}
