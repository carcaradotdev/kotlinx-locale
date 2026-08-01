package dev.carcara.kotlinx.locale.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.NumberFormatSource
import dev.carcara.kotlinx.locale.number.PluralRuleSource
import dev.carcara.kotlinx.locale.number.PluralType
import dev.carcara.kotlinx.locale.number.format
import dev.carcara.kotlinx.locale.number.pluralCategory
import dev.carcara.kotlinx.locale.number.symbols
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Holds a plural source to CLDR's own samples.
 *
 * `plurals.xml` and `ordinals.xml` write an `@integer` and `@decimal` list under
 * every rule saying which values fall into it. Asserting that the shipped
 * evaluator agrees is CLDR checking this library with CLDR's data, independently
 * of ICU, on every target this compiles for. It is the cheapest high-value
 * fixture in the number domain, because plural selection is a pure function and
 * the samples are a complete statement of intent.
 *
 * The samples keep their written form rather than being parsed into numbers: in
 * Czech `1` is `one` and `1.0` is `many`, and the difference is the count of
 * visible fraction digits, which a numeric type would lose.
 */
public fun PluralRuleSource.assertConformsToCldrPluralSamples() {
    assertTrue(cldrPluralSamples.size > 1000, "expected the full sample set, got ${cldrPluralSamples.size}")
    var checked = 0
    for (sample in cldrPluralSamples) {
        val locale = Locale.forLanguageTagOrNull(sample.tag) ?: continue
        if (locale !in supportedLocales) continue
        val value = Decimal.parseOrNull(sample.value) ?: continue
        val type = if (sample.isOrdinal) PluralType.ORDINAL else PluralType.CARDINAL
        assertEquals(
            sample.category,
            pluralCategory(value, value.scale, locale, type).cldrName,
            "${sample.tag} ${if (sample.isOrdinal) "ordinal" else "cardinal"} ${sample.value}",
        )
        checked++
    }
    assertTrue(checked > 1000, "expected to check the full sample set, checked only $checked")
}

/**
 * Runs a number source through the shape checks every implementation owes,
 * regardless of where its data came from.
 */
public fun NumberFormatSource.assertNumbersAreWellShaped() {
    val english = Locale.of("en")
    assertTrue(supportedLocales.isNotEmpty(), "a CLDR-backed source is expected to enumerate its locales")

    for (locale in supportedLocales) {
        val symbols = symbols(locale)
        assertEquals(10, symbols.digits.size, "$locale does not carry ten digits")
        assertTrue(symbols.decimal.isNotEmpty(), "$locale has no decimal separator")
        assertTrue(symbols.minimumGroupingDigits >= 1, "$locale has an impossible minimumGroupingDigits")
        assertTrue(format(1234567L, locale).isNotBlank(), "$locale formatted nothing")
    }

    // A number round trips through its own locale, which is what makes the
    // separators the source hands out the same ones it prints.
    for (locale in listOf(english, Locale.of("de"), Locale.of("cs"), Locale.of("pl"))) {
        val original = Decimal.parse("1234.56")
        val formatted = format(original, locale, minimumFractionDigits = 2, maximumFractionDigits = 2)
        val parsed = assertNotNull(parseDecimalOrNull(formatted, locale), "$locale could not read back '$formatted'")
        assertEquals(0, original.compareTo(parsed), "$locale round trip: '$formatted' read back as $parsed")
    }
}
