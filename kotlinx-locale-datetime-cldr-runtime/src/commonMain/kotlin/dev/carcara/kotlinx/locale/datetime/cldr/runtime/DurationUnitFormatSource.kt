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

@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.datetime.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource
import dev.carcara.kotlinx.locale.internal.ENTRY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.KEY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.resolvedRecord
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.NumberFormatSource
import dev.carcara.kotlinx.locale.number.PluralCategory
import dev.carcara.kotlinx.locale.number.PluralRuleSource
import dev.carcara.kotlinx.locale.number.format
import dev.carcara.kotlinx.locale.number.pluralCategory

/**
 * The time units CLDR carries measurement wording for, largest first.
 *
 * Not [kotlin.time.DurationUnit], which names the same idea for arithmetic and
 * stops at days. These are the `duration-*` units of UTS #35 Part 6, and they
 * exist to be written down rather than converted between: nothing here turns
 * ninety minutes into an hour and a half.
 *
 * CLDR carries two more that this list leaves out. `duration-fortnight` reaches
 * twelve locales in release-48-2 and `duration-day-person` reaches sixteen, so
 * each would be a fifteenth of the table for wording almost nobody could read.
 * Leaving them out is a decision rather than an omission, and the same one
 * `RelativeTimeUnit` makes about the per-weekday fields.
 *
 * The order is the one the encoded record uses and has to match
 * `DURATION_UNITS` in `:codegen`.
 */
public enum class DurationUnit {
    CENTURY,
    DECADE,
    YEAR,
    QUARTER,
    MONTH,
    WEEK,
    DAY,

    /** Nights, as a hotel counts a stay. CLDR keys it separately from [DAY]. */
    NIGHT,
    HOUR,
    MINUTE,
    SECOND,
    MILLISECOND,
    MICROSECOND,
    NANOSECOND,
    ;

    public companion object
}

/**
 * How wide the unit is written: CLDR's three `unitLength` values.
 *
 * In English an hour is `2 hours`, `2 hr` and `2h`. The widths are wording
 * rather than abbreviation, so a locale is free to make two of them identical,
 * and many do.
 */
public enum class UnitWidth {
    LONG,
    SHORT,
    NARROW,
    ;

    public companion object
}

/**
 * A source that writes a quantity of time the way a locale writes it.
 *
 * The pairing for [DurationPatternSource][dev.carcara.kotlinx.locale.datetime.DurationPatternSource],
 * which hands back `h:mm` and is a clock reading. This one is the measurement
 * form: `2 hours`, `2 std.`, `2 sa`, with the plural rules and the digits of the
 * locale applied.
 *
 * Which unit to use is the caller's, for the reason relative time gives at
 * length: CLDR carries the wording for a value and a unit and says nothing about
 * when ninety minutes should become an hour and a half.
 */
public interface DurationUnitFormatSource : LocaleDataSource {

    /**
     * [value] many [unit]s written for [locale], or `null` when this build
     * carries nothing for it.
     *
     * The plural form is selected from the number as it will be printed, which
     * is why this takes a [Decimal] rather than a number: in Czech `1 hodina`
     * and `1,0 hodiny` differ for the same value.
     */
    public fun durationFormatOrNull(value: Decimal, unit: DurationUnit, width: UnitWidth, locale: Locale): String?

    /** The locale's name for [unit] itself: `hours`, `Stunden`, or `null`. */
    public fun durationUnitNameOrNull(unit: DurationUnit, width: UnitWidth, locale: Locale): String?

    public companion object
}

/**
 * [value] many [unit]s written for [locale], keeping the digits [value] carries.
 *
 * Falls back to a bare English `2 hours` when this build has nothing for the
 * locale, so the call is total.
 */
public fun DurationUnitFormatSource.durationFormat(
    value: Decimal,
    unit: DurationUnit,
    width: UnitWidth = UnitWidth.LONG,
    locale: Locale = Locale.current,
): String = durationFormatOrNull(value, unit, width, locale) ?: fallbackWording(value, unit)

/** [value] many [unit]s, with no fraction digits. */
public fun DurationUnitFormatSource.durationFormat(
    value: Long,
    unit: DurationUnit,
    width: UnitWidth = UnitWidth.LONG,
    locale: Locale = Locale.current,
): String = durationFormat(Decimal.of(value), unit, width, locale)

/**
 * [value] many [unit]s at exactly [fractionDigits] digits.
 *
 * The digit count is required rather than read off the float, for the reason
 * [Decimal] gives: the targets do not agree on how many digits a `Double` has,
 * and the plural category depends on how many are printed.
 */
public fun DurationUnitFormatSource.durationFormat(
    value: Double,
    fractionDigits: Int,
    unit: DurationUnit,
    width: UnitWidth = UnitWidth.LONG,
    locale: Locale = Locale.current,
): String {
    val decimal = Decimal.ofOrNull(value, fractionDigits) ?: return "$value ${unit.name.lowercase()}s"
    return durationFormat(decimal, unit, width, locale)
}

/** The locale's name for [unit]; falls back to the English enum name. */
public fun DurationUnitFormatSource.durationUnitName(
    unit: DurationUnit,
    width: UnitWidth = UnitWidth.LONG,
    locale: Locale = Locale.current,
): String = durationUnitNameOrNull(unit, width, locale) ?: unit.name.lowercase()

private fun fallbackWording(value: Decimal, unit: DurationUnit): String {
    val name = unit.name.lowercase()
    val plural = if (value == Decimal.of(1)) name else "${name}s"
    return "${value.toPlainString()} $plural"
}

/** Answers from [primary], and from [fallback] wherever primary has nothing. */
public class FallbackDurationUnitFormats(private val primary: DurationUnitFormatSource, private val fallback: DurationUnitFormatSource) :
    DurationUnitFormatSource {

    override val supportedLocales: Set<Locale> get() = primary.supportedLocales + fallback.supportedLocales

    override fun durationFormatOrNull(value: Decimal, unit: DurationUnit, width: UnitWidth, locale: Locale): String? =
        primary.durationFormatOrNull(value, unit, width, locale) ?: fallback.durationFormatOrNull(value, unit, width, locale)

    override fun durationUnitNameOrNull(unit: DurationUnit, width: UnitWidth, locale: Locale): String? =
        primary.durationUnitNameOrNull(unit, width, locale) ?: fallback.durationUnitNameOrNull(unit, width, locale)

    public companion object
}

/** The display name, then one pattern per plural category. */
private const val SLOTS_PER_UNIT = 7
private const val CATEGORY_BASE = 1

/** The plural categories in the order the record stores them. */
private val CATEGORY_ORDER = listOf(
    PluralCategory.ZERO,
    PluralCategory.ONE,
    PluralCategory.TWO,
    PluralCategory.FEW,
    PluralCategory.MANY,
    PluralCategory.OTHER,
)

/**
 * One locale's duration wording: three width blocks of fourteen units.
 *
 * A short or narrow block that resolved to the same wording as the long one is
 * stored empty and the reader falls back to it. Resolving the widths at
 * generation time is what makes that possible, the same way the relative-time
 * record does: a `↑↑↑` under a narrow unit means "the same as long in this
 * locale", which a per-locale chain walk cannot express.
 */
@InternalKotlinxLocaleApi
public class DurationUnitRecord(record: String) {

    private val blocks: List<List<List<String>>> = record.split(FIELD_SEPARATOR).map { block ->
        if (block.isEmpty()) emptyList() else block.split(ENTRY_SEPARATOR).map { it.split(KEY_SEPARATOR) }
    }

    private fun slot(unit: DurationUnit, width: UnitWidth, index: Int): String? {
        for (level in widthOrder(width)) {
            val block = blocks.getOrNull(level) ?: continue
            val slots = block.getOrNull(unit.ordinal) ?: continue
            if (slots.size < SLOTS_PER_UNIT) continue
            slots[index].takeIf(String::isNotEmpty)?.let { return it }
        }
        return null
    }

    /** The asked-for width, then wider, which is what an empty block defers to. */
    private fun widthOrder(width: UnitWidth): List<Int> = when (width) {
        UnitWidth.LONG -> listOf(0)
        UnitWidth.SHORT -> listOf(1, 0)
        UnitWidth.NARROW -> listOf(2, 1, 0)
    }

    public fun unitName(unit: DurationUnit, width: UnitWidth): String? = slot(unit, width, 0)

    /**
     * The counting pattern for [category], falling back to `other` within a width
     * before trying the next one.
     *
     * The order matters and the generator applies the same one. German writes a
     * short century only in `other`, so `1` reads `1 Jh.` out of the short block
     * rather than `1 Jahrhundert` out of the long one.
     */
    public fun pattern(unit: DurationUnit, width: UnitWidth, category: PluralCategory): String? {
        val wanted = CATEGORY_BASE + CATEGORY_ORDER.indexOf(category)
        val other = CATEGORY_BASE + CATEGORY_ORDER.indexOf(PluralCategory.OTHER)
        for (level in widthOrder(width)) {
            val slots = blocks.getOrNull(level)?.getOrNull(unit.ordinal) ?: continue
            if (slots.size < SLOTS_PER_UNIT) continue
            slots[wanted].takeIf(String::isNotEmpty)?.let { return it }
            slots[other].takeIf(String::isNotEmpty)?.let { return it }
        }
        return null
    }

    public companion object
}

/**
 * A duration-unit source over the generated tables.
 *
 * Takes the plural rules and the number formatter as constructor arguments
 * rather than reaching for them, so this module depends on the number interfaces
 * and not on the number tables. `{0}` is rendered through the formatter, which is
 * what puts Arabic-Indic digits in an Arabic phrase.
 */
public class PayloadDurationUnitFormats(
    private val records: Map<String, String>,
    private val plurals: PluralRuleSource,
    private val numbers: NumberFormatSource,
) : DurationUnitFormatSource {

    override val supportedLocales: Set<Locale> by lazy { supportedLocalesOf(records) }

    override fun durationFormatOrNull(value: Decimal, unit: DurationUnit, width: UnitWidth, locale: Locale): String? {
        val record = recordFor(locale) ?: return null
        val category = plurals.pluralCategory(value, value.scale, locale)
        val pattern = record.pattern(unit, width, category) ?: return null
        // Pinned to the value's own scale on both sides, because the category was
        // chosen from that scale. Letting the formatter drop a trailing zero would
        // print "2.5 hodiny" having selected the form for 2.50.
        val digits = numbers.format(
            value,
            locale,
            minimumFractionDigits = value.scale,
            maximumFractionDigits = value.scale,
        )
        return pattern.replace("{0}", digits)
    }

    override fun durationUnitNameOrNull(unit: DurationUnit, width: UnitWidth, locale: Locale): String? =
        recordFor(locale)?.unitName(unit, width)

    private fun recordFor(locale: Locale): DurationUnitRecord? = resolvedRecord(records, locale)?.let(::DurationUnitRecord)

    public companion object
}
