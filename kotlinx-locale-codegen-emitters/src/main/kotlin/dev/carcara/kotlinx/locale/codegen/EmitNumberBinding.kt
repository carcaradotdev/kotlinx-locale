package dev.carcara.kotlinx.locale.codegen

import java.io.File

/**
 * `CldrNumber`-shaped binding: the source object plus the number extensions.
 *
 * [hasCompact] and [hasOrdinals] decide whether the compact tables and the
 * ordinal rules are wired in. A build that did not ask for them gets an object
 * with empty tables rather than a missing symbol, so compact notation degrades
 * to the standard pattern and an ordinal degrades to its digits, which is what
 * those calls fall back to anyway when a locale has no data.
 */
public fun emitNumberBinding(outputRoot: File, spec: BindingSpec, hasCompact: Boolean, hasOrdinals: Boolean) {
    val file = outputRoot.packageFile(spec.packageName, "NumberFormat.kt")
    val imports = buildList {
        add("dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi")
        add("dev.carcara.kotlinx.locale.Locale")
        add("dev.carcara.kotlinx.locale.number.Decimal")
        add("dev.carcara.kotlinx.locale.number.NumberFormatSource")
        add("dev.carcara.kotlinx.locale.number.NumberGrouping")
        add("dev.carcara.kotlinx.locale.number.NumberNotation")
        add("dev.carcara.kotlinx.locale.number.NumberSymbols")
        add("dev.carcara.kotlinx.locale.number.OrdinalFormatSource")
        add("dev.carcara.kotlinx.locale.number.PluralCategory")
        add("dev.carcara.kotlinx.locale.number.PluralRuleSource")
        add("dev.carcara.kotlinx.locale.number.PluralType")
        add("dev.carcara.kotlinx.locale.number.SignDisplay")
        add("dev.carcara.kotlinx.locale.number.cldr.runtime.PayloadNumberFormats")
        add("dev.carcara.kotlinx.locale.number.cldr.runtime.PayloadOrdinalFormats")
        add("dev.carcara.kotlinx.locale.number.cldr.runtime.PayloadPluralRules")
        add("dev.carcara.kotlinx.locale.number.format")
        add("dev.carcara.kotlinx.locale.number.formatPercent")
        add("dev.carcara.kotlinx.locale.number.formatPercentValue")
        add("dev.carcara.kotlinx.locale.number.ordinal")
        add("dev.carcara.kotlinx.locale.number.pluralCategory")
        add("dev.carcara.kotlinx.locale.number.symbols")
        add("${spec.registryPackage}.numberPatternsRegistry")
        add("${spec.registryPackage}.numberSymbolsRegistry")
        add("${spec.registryPackage}.pluralRuleIndexRegistry")
        add("${spec.registryPackage}.pluralRuleSetsRegistry")
        if (hasCompact) {
            add("${spec.registryPackage}.compactLongRegistry")
            add("${spec.registryPackage}.compactShortRegistry")
        }
        if (hasOrdinals) {
            add("${spec.registryPackage}.ordinalRuleIndexRegistry")
            add("${spec.registryPackage}.ordinalRuleSetsRegistry")
        }
    }
    val compactShort = if (hasCompact) "compactShortRegistry" else "emptyMap()"
    val compactLong = if (hasCompact) "compactLongRegistry" else "emptyMap()"
    val ordinalSets = if (hasOrdinals) "ordinalRuleSetsRegistry" else "emptyMap()"
    val ordinalIndex = if (hasOrdinals) "ordinalRuleIndexRegistry" else "emptyMap()"

    file.writeText(
        preamble(spec, imports, "@file:OptIn(InternalKotlinxLocaleApi::class)") + """
        |
        |/**
        | * The plural rules this build carries.
        | *
        | * Its own object rather than a member of [${spec.objectName}], because the rules
        | * are useful without a formatter: a caller picking between translated
        | * strings needs the category and nothing else.
        | */
        |public object ${spec.objectName}Plurals : PluralRuleSource by PayloadPluralRules(
        |    pluralRuleSetsRegistry,
        |    pluralRuleIndexRegistry,
        |)
        |
        |/**
        | * The number formats this build carries.
        | *
        | * The formatting lives in `kotlinx-locale-number-cldr-runtime`; all this
        | * object contributes is the tables.
        | */
        |public object ${spec.objectName} : NumberFormatSource by PayloadNumberFormats(
        |    numberSymbolsRegistry,
        |    numberPatternsRegistry,
        |    $compactShort,
        |    $compactLong,
        |    ${spec.objectName}Plurals,
        |) {
        |
        |    /**
        |     * The symbol table itself, for a domain layered over this one.
        |     *
        |     * `kotlinx-locale-currency-cldr-full` reads it rather than carrying a
        |     * second copy of every locale's separators.
        |     */
        |    @InternalKotlinxLocaleApi
        |    public val symbolRecords: Map<String, String> get() = numberSymbolsRegistry
        |}
        |
        |/** The ordinal forms this build carries: `1st`, `1.`, `1º`. */
        |public object ${spec.objectName}Ordinals : OrdinalFormatSource by PayloadOrdinalFormats(
        |    $ordinalSets,
        |    $ordinalIndex,
        |    ${spec.objectName},
        |    ${spec.objectName}Plurals,
        |)
        |
        |/** This number written for [locale], with its grouping separators. */
        |public fun Long.format(
        |    locale: Locale = Locale.current,
        |    notation: NumberNotation = NumberNotation.STANDARD,
        |    signDisplay: SignDisplay = SignDisplay.AUTO,
        |    grouping: NumberGrouping = NumberGrouping.AUTO,
        |): String = ${spec.objectName}.format(this, locale, notation, signDisplay, grouping)
        |
        |/**
        | * This number written for [locale] at exactly [fractionDigits] digits.
        | *
        | * The digit count is required rather than read off the float, because the
        | * targets do not agree on how many digits a `Double` has.
        | */
        |public fun Double.format(
        |    fractionDigits: Int,
        |    locale: Locale = Locale.current,
        |    notation: NumberNotation = NumberNotation.STANDARD,
        |    signDisplay: SignDisplay = SignDisplay.AUTO,
        |    grouping: NumberGrouping = NumberGrouping.AUTO,
        |): String = ${spec.objectName}.format(this, fractionDigits, locale, notation, signDisplay, grouping)
        |
        |/** This decimal written for [locale], keeping the digits it carries. */
        |public fun Decimal.format(
        |    locale: Locale = Locale.current,
        |    notation: NumberNotation = NumberNotation.STANDARD,
        |    signDisplay: SignDisplay = SignDisplay.AUTO,
        |    grouping: NumberGrouping = NumberGrouping.AUTO,
        |    minimumFractionDigits: Int? = null,
        |    maximumFractionDigits: Int? = null,
        |): String = ${spec.objectName}.format(
        |    this,
        |    locale,
        |    notation,
        |    signDisplay,
        |    grouping,
        |    minimumFractionDigits,
        |    maximumFractionDigits,
        |)
        |
        |/** This number in compact notation: `1200` is `1.2K` in `en`. */
        |public fun Long.formatCompact(
        |    locale: Locale = Locale.current,
        |    notation: NumberNotation = NumberNotation.COMPACT_SHORT,
        |): String = ${spec.objectName}.format(this, locale, notation)
        |
        |/** This decimal in compact notation. */
        |public fun Decimal.formatCompact(
        |    locale: Locale = Locale.current,
        |    notation: NumberNotation = NumberNotation.COMPACT_SHORT,
        |): String = ${spec.objectName}.format(this, locale, notation)
        |
        |/**
        | * This fraction written as a percentage: `0.075` in `en` is `7.5%`.
        | *
        | * Multiplies by 100, which is what a `%` in a CLDR pattern means and what
        | * `Intl.NumberFormat` does. For a value that is already scaled, use
        | * [formatPercentValue].
        | */
        |public fun Decimal.formatPercent(
        |    locale: Locale = Locale.current,
        |    fractionDigits: Int? = null,
        |    signDisplay: SignDisplay = SignDisplay.AUTO,
        |): String = ${spec.objectName}.formatPercent(this, locale, fractionDigits, signDisplay)
        |
        |/** This already-scaled percentage written for [locale]: `7.5` is `7.5%`. */
        |public fun Decimal.formatPercentValue(
        |    locale: Locale = Locale.current,
        |    fractionDigits: Int? = null,
        |    signDisplay: SignDisplay = SignDisplay.AUTO,
        |): String = ${spec.objectName}.formatPercentValue(this, locale, fractionDigits, signDisplay)
        |
        |/**
        | * [locale]'s number symbols: its digits, separators and signs.
        | *
        | * For building something this library does not format. An amount field that
        | * formats while someone types cannot round trip through a formatter, because
        | * that would normalise away the half-finished states the caret depends on.
        | */
        |public fun numberSymbols(locale: Locale = Locale.current): NumberSymbols = ${spec.objectName}.symbols(locale)
        |
        |/** A formatted number read back, or `null` when [text] does not parse in [locale]. */
        |public fun parseNumberOrNull(text: String, locale: Locale = Locale.current): Decimal? =
        |    ${spec.objectName}.parseDecimalOrNull(text, locale)
        |
        |/**
        | * The plural category of this count in [locale].
        | *
        | * An integer is the one case where a raw number is enough: it has no visible
        | * fraction digits, so nothing a formatting choice could change is left.
        | */
        |public fun Long.pluralCategory(
        |    locale: Locale = Locale.current,
        |    type: PluralType = PluralType.CARDINAL,
        |): PluralCategory = ${spec.objectName}Plurals.pluralCategory(this, locale, type)
        |
        |/**
        | * The plural category of this value shown with [fractionDigits] digits.
        | *
        | * The digit count is required: in Czech `1` is `one` and `1.0` is `many`.
        | */
        |public fun Decimal.pluralCategory(
        |    fractionDigits: Int,
        |    locale: Locale = Locale.current,
        |    type: PluralType = PluralType.CARDINAL,
        |): PluralCategory = ${spec.objectName}Plurals.pluralCategory(this, fractionDigits, locale, type)
        |
        |/** This number as an ordinal in [locale]: `1st`, `1.`, `1º`. */
        |public fun Long.formatOrdinal(locale: Locale = Locale.current): String =
        |    ${spec.objectName}Ordinals.ordinal(this, locale)
        |
        """.trimMargin(),
    )
    println("[codegen] emitted ${spec.objectName} to $file")
}
