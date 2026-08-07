@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.datetime.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.RelativeTimeFormatSource
import dev.carcara.kotlinx.locale.datetime.RelativeTimeNumbering
import dev.carcara.kotlinx.locale.datetime.RelativeTimeStyle
import dev.carcara.kotlinx.locale.datetime.RelativeTimeUnit
import dev.carcara.kotlinx.locale.internal.ENTRY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.KEY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.resolvedRecord
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf
import dev.carcara.kotlinx.locale.number.FormattedNumber
import dev.carcara.kotlinx.locale.number.NumberFormatSource
import dev.carcara.kotlinx.locale.number.PluralCategory
import dev.carcara.kotlinx.locale.number.PluralRuleSource
import dev.carcara.kotlinx.locale.number.PluralType
import dev.carcara.kotlinx.locale.number.format

/** displayName, then five literals for -2..2, then six future and six past plural forms. */
private const val SLOTS_PER_UNIT = 18
private const val LITERAL_BASE = 1
private const val FUTURE_BASE = 6
private const val PAST_BASE = 12

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
 * One locale's relative-time wording: three width blocks of eight units.
 *
 * A short or narrow block that resolved to the same wording as the base is
 * stored empty, and the reader falls back to block zero. Resolving the widths at
 * generation time rather than at runtime is what makes that possible: a `↑↑↑` in
 * `day-short` means "the same as `day` in this locale", which a per-locale chain
 * walk cannot express.
 */
@InternalKotlinxLocaleApi
public class RelativeTimeRecord(record: String) {

    private val blocks: List<List<List<String>>> = record.split(FIELD_SEPARATOR).map { block ->
        if (block.isEmpty()) {
            emptyList()
        } else {
            block.split(ENTRY_SEPARATOR).map { it.split(KEY_SEPARATOR) }
        }
    }

    private fun slot(unit: RelativeTimeUnit, style: RelativeTimeStyle, index: Int): String? {
        for (width in styleOrder(style)) {
            val block = blocks.getOrNull(width) ?: continue
            val slots = block.getOrNull(unit.ordinal) ?: continue
            if (slots.size < SLOTS_PER_UNIT) continue
            slots[index].takeIf(String::isNotEmpty)?.let { return it }
        }
        return null
    }

    /** The asked-for width, then narrower to wider, which is what an empty block defers to. */
    private fun styleOrder(style: RelativeTimeStyle): List<Int> = when (style) {
        RelativeTimeStyle.FULL -> listOf(0)
        RelativeTimeStyle.SHORT -> listOf(1, 0)
        RelativeTimeStyle.NARROW -> listOf(2, 1, 0)
    }

    public fun unitName(unit: RelativeTimeUnit, style: RelativeTimeStyle): String? = slot(unit, style, 0)

    /** The word for exactly [offset] units, or `null` when the locale has none. */
    public fun literal(offset: Int, unit: RelativeTimeUnit, style: RelativeTimeStyle): String? {
        if (offset !in -2..2) return null
        return slot(unit, style, LITERAL_BASE + offset + 2)
    }

    /** The counting pattern for [category], future or past, falling back to `other`. */
    public fun pattern(unit: RelativeTimeUnit, style: RelativeTimeStyle, future: Boolean, category: PluralCategory): String? {
        val base = if (future) FUTURE_BASE else PAST_BASE
        return slot(unit, style, base + CATEGORY_ORDER.indexOf(category))
            ?: slot(unit, style, base + CATEGORY_ORDER.indexOf(PluralCategory.OTHER))
    }

    public companion object
}

/**
 * A relative-time source over the generated tables.
 *
 * Takes the plural rules and the number formatter as constructor arguments
 * rather than reaching for them, so this module depends on the number interfaces
 * and not on the number tables. `{0}` is rendered through the formatter, which is
 * what puts Arabic-Indic digits in an Arabic sentence.
 */
public class PayloadRelativeTimeFormats(
    private val records: Map<String, String>,
    private val plurals: PluralRuleSource,
    private val numbers: NumberFormatSource,
) : RelativeTimeFormatSource {

    override val supportedLocales: Set<Locale> by lazy { supportedLocalesOf(records) }

    override fun formatOrNull(
        value: Long,
        unit: RelativeTimeUnit,
        style: RelativeTimeStyle,
        numbering: RelativeTimeNumbering,
        locale: Locale,
    ): String? {
        val record = recordFor(locale) ?: return null
        if (numbering == RelativeTimeNumbering.AUTO && value in -2L..2L) {
            record.literal(value.toInt(), unit, style)?.let { return it }
        }
        val magnitude = if (value < 0) -value else value
        val digits = magnitude.toString()
        val category = plurals.pluralCategoryOrNull(
            FormattedNumber(digits, digits, ""),
            PluralType.CARDINAL,
            locale,
        ) ?: PluralCategory.OTHER
        val pattern = record.pattern(unit, style, future = value >= 0, category = category) ?: return null
        return pattern.replace("{0}", numbers.format(magnitude, locale))
    }

    override fun literalOrNull(offset: Int, unit: RelativeTimeUnit, style: RelativeTimeStyle, locale: Locale): String? =
        recordFor(locale)?.literal(offset, unit, style)

    override fun unitNameOrNull(unit: RelativeTimeUnit, style: RelativeTimeStyle, locale: Locale): String? =
        recordFor(locale)?.unitName(unit, style)

    private fun recordFor(locale: Locale): RelativeTimeRecord? = resolvedRecord(records, locale)?.let(::RelativeTimeRecord)

    public companion object
}
