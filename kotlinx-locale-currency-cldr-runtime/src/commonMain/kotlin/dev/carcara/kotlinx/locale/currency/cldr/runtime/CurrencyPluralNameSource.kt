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

package dev.carcara.kotlinx.locale.currency.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.CurrencyNameSource
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.currency.forCodeOrNull
import dev.carcara.kotlinx.locale.internal.KEY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.sparseRecordValue
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf
import dev.carcara.kotlinx.locale.number.NumberFormatOptions
import dev.carcara.kotlinx.locale.number.NumberGrouping
import dev.carcara.kotlinx.locale.number.NumberSymbols
import dev.carcara.kotlinx.locale.number.PluralCategory
import dev.carcara.kotlinx.locale.number.PluralRuleSource
import dev.carcara.kotlinx.locale.number.SignDisplay
import dev.carcara.kotlinx.locale.number.cldr.runtime.NumberPattern
import dev.carcara.kotlinx.locale.number.cldr.runtime.digitStringsOf
import dev.carcara.kotlinx.locale.number.cldr.runtime.renderNumber
import dev.carcara.kotlinx.locale.number.pluralCategory

/**
 * Everything the name form of a currency can be asked for beyond the amount and
 * the locale.
 *
 * The same options [CurrencyFormatOptions][dev.carcara.kotlinx.locale.currency.CurrencyFormatOptions]
 * carries, less the two that have nothing to act on here. There is no symbol
 * style, because writing the name is the style. There is no notation, because
 * compact wording is rendered from the plain compact table rather than the
 * currency one, and that table lives in the number domain: `1.2M US dollars`
 * would mean this artifact carrying a second copy of it.
 */
public class CurrencyPluralFormatOptions(
    public val signDisplay: SignDisplay = SignDisplay.AUTO,
    /** CLDR's cash fraction digits and cash rounding: Swiss francs round to 0.05 in cash. */
    public val cash: Boolean = false,
    /** How many fraction digits to print, overriding CLDR's. */
    public val fractionDigits: Int? = null,
    public val grouping: NumberGrouping = NumberGrouping.AUTO,
) {

    public companion object {
        public val Default: CurrencyPluralFormatOptions = CurrencyPluralFormatOptions()
    }
}

/**
 * A source of currency names in words, and of amounts written with one.
 *
 * The pairing for [CurrencyNameSource], which hands back `$` and `US Dollar`.
 * This one is the counting form: `1.00 US dollar`, `2.00 US dollars`,
 * `5,00 amerických dolarů`, with the locale's plural rules deciding which
 * spelling the number in front of it takes.
 *
 * Plural throughout means chosen by the plural rules rather than more than one,
 * which is the sense CLDR and ICU use it in and the sense
 * [PluralCategory][dev.carcara.kotlinx.locale.number.PluralCategory] already has
 * here. `1 US dollar` is as much a plural name as `2 US dollars`: it is the
 * spelling the `one` category asks for.
 *
 * Not a sixth [CurrencySymbolStyle], although both reference implementations
 * present it as one: ICU as `NumberFormatter.UnitWidth.FULL_NAME` beside
 * `SHORT`, `NARROW`, `FORMAL`, `VARIANT` and `ISO_CODE`, and ECMA-402 as
 * `currencyDisplay: "name"` beside `"symbol"`, `"narrowSymbol"` and `"code"`.
 * UTS #35 lists a `¤¤¤` placeholder next to `¤`, `¤¤` and `¤¤¤¤¤` that reads the
 * same way.
 *
 * The data does not. No locale in CLDR `release-48-2` writes `¤¤¤` in a currency
 * pattern, or `¤¤` or `¤¤¤¤¤` either: all 10,375 currency patterns use a bare
 * `¤`. The name form arrives through a different pair of elements entirely,
 * `unitPattern` and the count-keyed `displayName`, and it is joined to a number
 * written through the plain decimal pattern rather than substituted into a
 * currency one. ICU follows the data rather than the placeholder table:
 * `LongNameHandler` builds this width and `MutablePatternModifier` builds the
 * other five.
 *
 * That seam is visible from outside. A currency that declares its own pattern in
 * a locale, which CLDR does once for TRY in Turkish, routes `FULL_NAME` back
 * through the pattern modifier, whose switch over the widths throws rather than
 * handling it. So the one axis is a presentation over two mechanisms, and this
 * library splits at the mechanism: `style` selects among the spellings that
 * substitute into a currency pattern, and the name form is its own call in its
 * own artifact. Which also keeps `format` honest, since the artifact that does
 * not carry these names has no style it must half-answer.
 */
public interface CurrencyPluralNameSource : LocaleDataSource {

    /**
     * The name this locale gives the currency in the form [category] takes, or
     * `null` when this source has none.
     *
     * The category belongs to the number as it will be printed rather than to
     * its value, which is why [formatPluralName] selects it rather than taking it: in
     * Czech `1` is `one` and `1,00` is `many`, so the same amount at two digit
     * counts wants two spellings.
     */
    public fun currencyPluralNameOrNull(currencyCode: String, category: PluralCategory, locale: Locale): String?

    /**
     * [minorUnits] of the currency with this code written with its name in
     * words, or `null` when this source cannot render it.
     */
    public fun formatPluralNameOrNull(minorUnits: Long, currencyCode: String, locale: Locale, options: CurrencyPluralFormatOptions): String?
}

/**
 * The name [currency] takes in [locale] in the form [category] asks for; falls
 * back to the ISO code.
 */
public fun CurrencyPluralNameSource.pluralName(currency: Currency, category: PluralCategory, locale: Locale): String =
    currencyPluralNameOrNull(currency.code, category, locale) ?: currency.code

/**
 * [amount] written for [locale] with the currency named in words.
 *
 * Falls back to `USD 12.50`, the ISO code and the plain ISO decimal, when the
 * source cannot render the amount at all.
 */
public fun CurrencyPluralNameSource.formatPluralName(
    amount: CurrencyAmount,
    locale: Locale,
    signDisplay: SignDisplay = SignDisplay.AUTO,
    cash: Boolean = false,
    fractionDigits: Int? = null,
    grouping: NumberGrouping = NumberGrouping.AUTO,
): String = formatPluralNameOrNull(
    amount.minorUnits,
    amount.currency.code,
    locale,
    CurrencyPluralFormatOptions(signDisplay, cash, fractionDigits, grouping),
) ?: amount.toString()

/** Answers from [primary], and from [fallback] wherever primary has nothing. */
public class FallbackCurrencyPluralNames(private val primary: CurrencyPluralNameSource, private val fallback: CurrencyPluralNameSource) :
    CurrencyPluralNameSource {

    override val supportedLocales: Set<Locale>
        get() = primary.supportedLocales + fallback.supportedLocales

    override fun currencyPluralNameOrNull(currencyCode: String, category: PluralCategory, locale: Locale): String? =
        primary.currencyPluralNameOrNull(currencyCode, category, locale)
            ?: fallback.currencyPluralNameOrNull(currencyCode, category, locale)

    override fun formatPluralNameOrNull(
        minorUnits: Long,
        currencyCode: String,
        locale: Locale,
        options: CurrencyPluralFormatOptions,
    ): String? = primary.formatPluralNameOrNull(minorUnits, currencyCode, locale, options)
        ?: fallback.formatPluralNameOrNull(minorUnits, currencyCode, locale, options)
}

/** The parent tag, the count-keyed names, the unit patterns, the number data. */
private const val FIELD_COUNT = 4
private const val NAMES_FIELD = 1
private const val UNIT_PATTERNS_FIELD = 2
private const val NUMBER_FIELD = 3

/**
 * The plural categories in the order the record stores them.
 *
 * Has to match `PLURAL_CATEGORIES` in `:codegen`, which is positional over this
 * list.
 */
private val CATEGORY_ORDER = listOf(
    PluralCategory.ZERO,
    PluralCategory.ONE,
    PluralCategory.TWO,
    PluralCategory.FEW,
    PluralCategory.MANY,
    PluralCategory.OTHER,
)

/** What root declares, which is what a locale with no record of its own answers. */
private val ROOT_UNIT_PATTERNS = List(CATEGORY_ORDER.size) { "{0} {1}" }
private const val ROOT_NUMBER_PATTERN = "#,##0.###"

/**
 * One locale's number formatting for the name form: the six patterns and the
 * symbols the digits are written with.
 *
 * Built once per locale rather than per call, because reaching either one is a
 * walk up the parent chain and an application formats the same locale over and
 * over.
 */
private class CurrencyPluralFormat(unitPatterns: String?, numberData: String?) {

    val unitPatterns: List<String> = unitPatterns?.split(KEY_SEPARATOR)
        ?.takeIf { it.size == CATEGORY_ORDER.size }
        ?: ROOT_UNIT_PATTERNS

    private val fields = numberData?.split(KEY_SEPARATOR).orEmpty()

    val pattern: NumberPattern = NumberPattern.parse(fields.getOrNull(5) ?: ROOT_NUMBER_PATTERN)

    /**
     * The currency decimal and group separators sit in the plain slots as well
     * as the currency ones, because this record carries no plain pair: the name
     * form always reads the currency separators, the way ICU does.
     */
    val symbols: NumberSymbols = NumberSymbols(
        numberingSystem = "latn",
        digits = digitStringsOf(fields.getOrNull(0) ?: "0123456789"),
        decimal = fields.getOrNull(1) ?: ".",
        group = fields.getOrNull(2) ?: ",",
        currencyDecimal = fields.getOrNull(1) ?: ".",
        currencyGroup = fields.getOrNull(2) ?: ",",
        minusSign = fields.getOrNull(3) ?: "-",
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
        minimumGroupingDigits = fields.getOrNull(4)?.toIntOrNull() ?: 1,
    )

    fun unitPattern(category: PluralCategory): String = unitPatterns[CATEGORY_ORDER.indexOf(category)]
}

/**
 * A [CurrencyPluralNameSource] over a table of CLDR count-keyed name records.
 *
 * Takes the count-less names and the plural rules as constructor arguments
 * rather than reaching for them, so this module depends on the interfaces and
 * not on either table. [names] is the third step of the fallback chain and is
 * why this composes with the currency binding rather than carrying its own copy
 * of four thousand display names.
 */
public class PayloadCurrencyPluralNames(
    private val records: Map<String, String>,
    private val names: CurrencyNameSource,
    private val plurals: PluralRuleSource,
) : CurrencyPluralNameSource {

    private val formatCache = HashMap<String, CurrencyPluralFormat>()

    override val supportedLocales: Set<Locale> by lazy { supportedLocalesOf(records) }

    /**
     * UTS #35's fallback chain for a currency name, which ICU spells out in
     * `getPluralName`: the asked-for category, then `other`, then the count-less
     * display name. The fourth step is the ISO code and belongs to the caller,
     * because a source that answered it would stop a fallback chain at the first
     * link.
     */
    override fun currencyPluralNameOrNull(currencyCode: String, category: PluralCategory, locale: Locale): String? =
        nameOrNull(currencyCode, category.cldrName, locale)
            ?: nameOrNull(currencyCode, PluralCategory.OTHER.cldrName, locale)
            ?: names.currencyNameOrNull(currencyCode, locale)

    override fun formatPluralNameOrNull(
        minorUnits: Long,
        currencyCode: String,
        locale: Locale,
        options: CurrencyPluralFormatOptions,
    ): String? {
        val currency = Currency.forCodeOrNull(currencyCode) ?: return null
        val format = formatFor(locale)
        val scaled = scaleCurrencyAmount(minorUnits, currency, options.cash, options.fractionDigits)
        // Rendered before the category is chosen, because the category is a
        // property of the digits that came out: 1 filler is `one` in Hungarian
        // and 1.00 dollars is not `one` in Czech, from the same call.
        val number = renderNumber(
            value = scaled.value,
            pattern = format.pattern,
            symbols = format.symbols,
            options = NumberFormatOptions(signDisplay = options.signDisplay, grouping = options.grouping),
            fixedFractionDigits = scaled.fractionDigits,
            useCurrencySeparators = true,
        )
        val category = plurals.pluralCategory(number, locale)
        val name = currencyPluralNameOrNull(currencyCode, category, locale) ?: currencyCode
        return substitute(format.unitPattern(category), number.text, name)
    }

    /**
     * Stops before root, which UTS #35 asks for by name and a narrowed build
     * makes load bearing: root there is the fallback locale's flattened record,
     * and reading it here would answer `2.00US dollars` for a Japanese amount
     * whose own display name is `米ドル`. The count-less step below does read
     * root, which is what makes an ungenerated locale answer in the fallback.
     */
    private fun nameOrNull(currencyCode: String, category: String, locale: Locale): String? = sparseRecordValue(
        records,
        locale,
        field = NAMES_FIELD,
        fieldCount = FIELD_COUNT,
        key = "$currencyCode#$category",
        stopBeforeRoot = true,
    )

    private fun formatFor(locale: Locale): CurrencyPluralFormat = formatCache.getOrPut(locale.toLanguageTag()) {
        CurrencyPluralFormat(
            sparseRecordValue(records, locale, field = UNIT_PATTERNS_FIELD, fieldCount = FIELD_COUNT, key = "u"),
            sparseRecordValue(records, locale, field = NUMBER_FIELD, fieldCount = FIELD_COUNT, key = "n"),
        )
    }
}

/**
 * `{0}` and `{1}` of a unit pattern, filled in one pass.
 *
 * One pass rather than two `replace` calls, so a currency name that happens to
 * contain a placeholder cannot be rewritten a second time.
 */
private fun substitute(pattern: String, number: String, name: String): String = buildString(pattern.length + number.length + name.length) {
    var index = 0
    while (index < pattern.length) {
        val open = pattern.indexOf('{', index)
        if (open < 0 || open + 2 >= pattern.length || pattern[open + 2] != '}') {
            append(pattern, index, pattern.length)
            return@buildString
        }
        append(pattern, index, open)
        when (pattern[open + 1]) {
            '0' -> append(number)
            '1' -> append(name)
            else -> append(pattern, open, open + 3)
        }
        index = open + 3
    }
}
