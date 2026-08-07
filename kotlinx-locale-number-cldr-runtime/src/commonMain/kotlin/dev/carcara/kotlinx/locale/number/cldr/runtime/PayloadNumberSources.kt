@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.number.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.internal.resolvedRecord
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.FormattedNumber
import dev.carcara.kotlinx.locale.number.NumberFormatOptions
import dev.carcara.kotlinx.locale.number.NumberFormatSource
import dev.carcara.kotlinx.locale.number.NumberNotation
import dev.carcara.kotlinx.locale.number.NumberSymbols
import dev.carcara.kotlinx.locale.number.OrdinalFormatSource
import dev.carcara.kotlinx.locale.number.PercentScale
import dev.carcara.kotlinx.locale.number.PluralCategory
import dev.carcara.kotlinx.locale.number.PluralRuleSource
import dev.carcara.kotlinx.locale.number.PluralType
import dev.carcara.kotlinx.locale.number.pluralCategory

/**
 * A number source over the generated tables.
 *
 * Decoded records are cached per language tag, the way the skeleton source
 * caches its own, because parsing a pattern is the expensive half and a screen
 * formats many numbers in one locale.
 */
public class PayloadNumberFormats(
    private val symbolRecords: Map<String, String>,
    private val patternRecords: Map<String, String>,
    private val compactShortRecords: Map<String, String>,
    private val compactLongRecords: Map<String, String>,
    private val plurals: PluralRuleSource,
) : NumberFormatSource {

    private val cache = HashMap<String, LocaleNumbers?>()

    override val supportedLocales: Set<Locale> get() = supportedLocalesOf(symbolRecords)

    override fun symbolsOrNull(locale: Locale): NumberSymbols? = numbersFor(locale)?.symbols

    override fun formatOrNull(value: Decimal, locale: Locale, options: NumberFormatOptions): FormattedNumber? {
        val numbers = numbersFor(locale) ?: return null
        return render(numbers, numbers.decimalPattern, value, locale, options)
    }

    override fun formatPercentOrNull(value: Decimal, locale: Locale, scale: PercentScale, options: NumberFormatOptions): FormattedNumber? {
        val numbers = numbersFor(locale) ?: return null
        // The percent pattern's own `%` multiplies by 100, so an already-scaled
        // value is divided first and the pattern puts it back. Doing it this way
        // rather than stripping the multiplier keeps one code path and keeps the
        // sign, grouping and affix handling identical between the two readings.
        val input = when (scale) {
            PercentScale.FRACTION -> value
            PercentScale.PERCENT -> Decimal.ofUnscaled(value.unscaled, minOf(value.scale + 2, 18))
        }
        return render(numbers, numbers.percentPattern, input, locale, options)
    }

    override fun parseDecimalOrNull(text: String, locale: Locale): Decimal? {
        val numbers = numbersFor(locale) ?: return null
        return parseDecimal(text, numbers.symbols)
    }

    private fun render(
        numbers: LocaleNumbers,
        pattern: NumberPattern,
        value: Decimal,
        locale: Locale,
        options: NumberFormatOptions,
    ): FormattedNumber = when (options.notation) {
        NumberNotation.STANDARD -> renderNumber(value, pattern, numbers.symbols, options)
        NumberNotation.COMPACT_SHORT -> formatCompact(
            value,
            numbers.compactShort,
            pattern,
            numbers.symbols,
            FormattedNumberSelector { plurals.pluralCategoryOrNull(it, PluralType.CARDINAL, locale) ?: PluralCategory.OTHER },
            options,
        )
        NumberNotation.COMPACT_LONG -> formatCompact(
            value,
            numbers.compactLong,
            pattern,
            numbers.symbols,
            FormattedNumberSelector { plurals.pluralCategoryOrNull(it, PluralType.CARDINAL, locale) ?: PluralCategory.OTHER },
            options,
        )
    }

    private fun numbersFor(locale: Locale): LocaleNumbers? {
        val key = locale.toLanguageTag()
        if (cache.containsKey(key)) return cache[key]
        val symbolRecord = resolvedRecord(symbolRecords, locale)
        val patternRecord = resolvedRecord(patternRecords, locale)
        val numbers = if (symbolRecord == null || patternRecord == null) {
            null
        } else {
            LocaleNumbers(
                symbols = NumberSymbolsRecord(symbolRecord).toSymbols(),
                patterns = NumberPatternRecord(patternRecord),
                compactShort = CompactPatternTable(resolvedRecord(compactShortRecords, locale).orEmpty()),
                compactLong = CompactPatternTable(resolvedRecord(compactLongRecords, locale).orEmpty()),
            )
        }
        cache[key] = numbers
        return numbers
    }

    public companion object
}

private class LocaleNumbers(
    val symbols: NumberSymbols,
    patterns: NumberPatternRecord,
    val compactShort: CompactPatternTable,
    val compactLong: CompactPatternTable,
) {
    val decimalPattern: NumberPattern = NumberPattern.parse(patterns.decimalPattern)
    val percentPattern: NumberPattern = NumberPattern.parse(patterns.percentPattern)
}

/**
 * A plural rule source over the generated tables.
 *
 * The rule sets are keyed by id rather than by locale, because 1122 locales
 * share about sixty-five of them, and the index maps a tag to the cardinal and
 * ordinal ids it uses.
 */
public class PayloadPluralRules(
    /** rule set id -> encoded conditions. */
    private val ruleSets: Map<String, String>,
    /** locale tag -> `"<cardinalId> <ordinalId>"`. */
    private val ruleIndex: Map<String, String>,
) : PluralRuleSource {

    private val parsed = HashMap<String, PluralRuleSet>()

    override val supportedLocales: Set<Locale> get() = supportedLocalesOf(ruleIndex)

    override fun pluralCategoryOrNull(number: FormattedNumber, type: PluralType, locale: Locale): PluralCategory? {
        val entry = resolvedRecord(ruleIndex, locale) ?: return null
        val ids = entry.split(' ')
        val id = when (type) {
            PluralType.CARDINAL -> ids.getOrNull(0)
            PluralType.ORDINAL -> ids.getOrNull(1)
        }?.takeIf { it.isNotEmpty() } ?: return PluralCategory.OTHER
        val ruleSet = parsed.getOrPut(id) { PluralRuleSet.parse(ruleSets[id].orEmpty()) }
        return ruleSet.select(number)
    }

    public companion object
}

/** An ordinal source over the generated rule closures. */
public class PayloadOrdinalFormats(
    /** closure id -> encoded rule sets. */
    private val closures: Map<String, String>,
    /** locale tag -> closure id. */
    private val index: Map<String, String>,
    private val numbers: NumberFormatSource,
    private val plurals: PluralRuleSource,
) : OrdinalFormatSource {

    private val parsed = HashMap<String, OrdinalRuleClosure>()

    override val supportedLocales: Set<Locale> get() = supportedLocalesOf(index)

    override fun ordinalOrNull(value: Long, locale: Locale): String? {
        val id = resolvedRecord(index, locale)?.takeIf { it.isNotEmpty() } ?: return null
        val closure = parsed.getOrPut(id) { OrdinalRuleClosure(closures[id].orEmpty()) }
        if (closure.isEmpty) return null
        val symbols = numbers.symbolsOrNull(locale) ?: NumberSymbols.Root
        return closure.format(value, symbols) { count ->
            plurals.pluralCategory(count, locale, PluralType.ORDINAL)
        }
    }

    public companion object
}
