package dev.carcara.kotlinx.locale.codegen

import java.io.File

/** `CldrCurrencyPlurals`-shaped binding: the source object plus the name extensions. */
public fun emitCurrencyPluralsBinding(outputRoot: File, spec: BindingSpec, currencyObject: String, numberObject: String) {
    val file = outputRoot.packageFile(spec.packageName, "CurrencyPlurals.kt")
    file.writeText(
        preamble(
            spec,
            listOf(
                "dev.carcara.kotlinx.locale.Locale",
                "dev.carcara.kotlinx.locale.currency.Currency",
                "dev.carcara.kotlinx.locale.currency.CurrencyAmount",
                "dev.carcara.kotlinx.locale.currency.cldr.runtime.CurrencyPluralNameSource",
                "dev.carcara.kotlinx.locale.currency.cldr.runtime.PayloadCurrencyPluralNames",
                "dev.carcara.kotlinx.locale.currency.cldr.runtime.formatPluralName",
                "dev.carcara.kotlinx.locale.currency.cldr.runtime.pluralName",
                "dev.carcara.kotlinx.locale.currency.code",
                "dev.carcara.kotlinx.locale.number.NumberGrouping",
                "dev.carcara.kotlinx.locale.number.PluralType",
                "dev.carcara.kotlinx.locale.number.SignDisplay",
                "dev.carcara.kotlinx.locale.number.pluralCategory",
                "${spec.registryPackage}.currencyPluralNamesRegistry",
            ),
        ) + """
        |
        |/**
        | * The currency names in words this build carries.
        | *
        | * The fallback chain and the pattern substitution live in
        | * `kotlinx-locale-currency-cldr-runtime`; all this object contributes is the
        | * table, the count-less names it falls back to, and the plural rules that
        | * choose between the forms.
        | */
        |public object ${spec.objectName} : CurrencyPluralNameSource by PayloadCurrencyPluralNames(
        |    currencyPluralNamesRegistry,
        |    $currencyObject,
        |    ${numberObject}Plurals,
        |)
        |
        |/**
        | * [this] written for [locale] with the currency named in words.
        | *
        | * ```
        | * CurrencyAmount(Currency.USD, 2_00).formatPluralName()            // "2.00 US dollars"
        | * CurrencyAmount(Currency.USD, 2_00).formatPluralName(cs)          // "2,00 amerického dolaru"
        | * CurrencyAmount(Currency.HUF, 1_00).formatPluralName(hu)          // "1 magyar forint"
        | * ```
        | *
        | * The number is written the way [locale] writes any number, which is not
        | * always the way it writes money: Malayalam groups `12,34,567.89 യു.എസ്. ഡോളർ`
        | * where the symbol form groups in threes. Only the decimal and group
        | * separators stay the currency pair, and ICU does the same.
        | *
        | * The form the name takes is chosen from the digits that come out rather than
        | * from the amount, so [fractionDigits] changes it: at CLDR's two digits a
        | * dollar reads `1.00 US dollars`, and at none it reads `1 US dollar`. Both are
        | * plural names in CLDR's sense of the word, which is the form the plural rules
        | * select rather than the form for more than one.
        | *
        | * [signDisplay] decides whether a sign appears. Its accounting values do not
        | * bring parentheses with them here, because those live in CLDR's accounting
        | * currency pattern and this form does not use a currency pattern at all.
        | * [cash] applies CLDR's cash fraction digits and cash rounding.
        | */
        |public fun CurrencyAmount.formatPluralName(
        |    locale: Locale = Locale.current,
        |    signDisplay: SignDisplay = SignDisplay.AUTO,
        |    cash: Boolean = false,
        |    fractionDigits: Int? = null,
        |    grouping: NumberGrouping = NumberGrouping.AUTO,
        |): String = ${spec.objectName}.formatPluralName(this, locale, signDisplay, cash, fractionDigits, grouping)
        |
        |/**
        | * The name [locale] gives this currency beside a count of [count]: `US dollar`
        | * for 1 and `US dollars` for 2. Falls back to the ISO code.
        | *
        | * A whole count, because that is where the answer is a property of the number
        | * alone. A printed `1.00` takes a different form from a printed `1` in much of
        | * Europe, and [formatPluralName] is the call that knows which is about to be
        | * written.
        | */
        |public fun Currency.pluralName(count: Long, locale: Locale = Locale.current): String = ${spec.objectName}.pluralName(
        |    this,
        |    ${numberObject}Plurals.pluralCategory(count, locale, PluralType.CARDINAL),
        |    locale,
        |)
        |
        """.trimMargin(),
    )
    println("[codegen] emitted ${spec.objectName} to $file")
}
