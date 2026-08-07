package dev.carcara.kotlinx.locale.number

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource

/**
 * A source of CLDR plural rules.
 *
 * The rules pick between translated forms: which of `den`, `dny`, `dne` and
 * `dní` follows a Czech number. This library carries the rules and the
 * categories; the words are the caller's.
 */
public interface PluralRuleSource : LocaleDataSource {

    /** The category [number] falls into for [locale], or `null` when this source has no rules for it. */
    public fun pluralCategoryOrNull(number: FormattedNumber, type: PluralType, locale: Locale): PluralCategory?

    public companion object
}

/**
 * The category for a number already formatted; falls back to
 * [PluralCategory.OTHER].
 *
 * Other is the category every locale has and the one CLDR uses when nothing
 * else matches, so falling back to it degrades to the general form rather than
 * to nothing.
 */
public fun PluralRuleSource.pluralCategory(
    number: FormattedNumber,
    locale: Locale = Locale.current,
    type: PluralType = PluralType.CARDINAL,
): PluralCategory = pluralCategoryOrNull(number, type, locale) ?: PluralCategory.OTHER

/**
 * The category for an integer count.
 *
 * Safe to take a raw number here and nowhere else: an integer has no visible
 * fraction digits, so the operands `v`, `w`, `f` and `t` are all zero by
 * construction and there is nothing a formatting choice could change.
 */
@OptIn(InternalKotlinxLocaleApi::class)
public fun PluralRuleSource.pluralCategory(
    count: Long,
    locale: Locale = Locale.current,
    type: PluralType = PluralType.CARDINAL,
): PluralCategory = pluralCategory(
    FormattedNumber(count.toString(), Decimal.of(count).absoluteDigits(), ""),
    locale,
    type,
)

/**
 * The category for [value] shown with exactly [fractionDigits] fraction digits.
 *
 * The digit count is required. In Czech `1` is `one` and `1.0` is `many`, so a
 * category for a decimal is not a property of the value alone; it is a property
 * of how the value is about to be printed.
 */
@OptIn(InternalKotlinxLocaleApi::class)
public fun PluralRuleSource.pluralCategory(
    value: Decimal,
    fractionDigits: Int,
    locale: Locale = Locale.current,
    type: PluralType = PluralType.CARDINAL,
): PluralCategory {
    val scaled = value.rescaled(fractionDigits)
    val digits = scaled.absoluteDigits().padStart(fractionDigits + 1, '0')
    val integerDigits = digits.substring(0, digits.length - fractionDigits)
    val fraction = digits.substring(digits.length - fractionDigits)
    return pluralCategory(FormattedNumber(scaled.toPlainString(), integerDigits, fraction), locale, type)
}

/** Answers from [primary], and from [fallback] wherever primary has nothing. */
public class FallbackPluralRules(private val primary: PluralRuleSource, private val fallback: PluralRuleSource) : PluralRuleSource {

    override val supportedLocales: Set<Locale> get() = primary.supportedLocales + fallback.supportedLocales

    override fun pluralCategoryOrNull(number: FormattedNumber, type: PluralType, locale: Locale): PluralCategory? =
        primary.pluralCategoryOrNull(number, type, locale) ?: fallback.pluralCategoryOrNull(number, type, locale)

    public companion object
}
