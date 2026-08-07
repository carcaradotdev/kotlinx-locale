package dev.carcara.kotlinx.locale.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.CurrencyFormatSource
import dev.carcara.kotlinx.locale.currency.CurrencyNameSource
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
import dev.carcara.kotlinx.locale.currency.active
import dev.carcara.kotlinx.locale.currency.cldrToIsoUnits
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.currency.displayName
import dev.carcara.kotlinx.locale.currency.forCodeOrNull
import dev.carcara.kotlinx.locale.currency.format
import dev.carcara.kotlinx.locale.currency.isoToCldrUnits
import dev.carcara.kotlinx.locale.currency.parseFormattedOrNull
import dev.carcara.kotlinx.locale.currency.symbol
import dev.carcara.kotlinx.locale.number.SignDisplay
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNotNull
import dev.carcara.kotlinx.locale.test.assertTrue

/** The locales the formatting checks run over, chosen for separator and digit variety. */
private val FORMAT_LOCALES = listOf("en", "de", "ja", "pt-BR", "ar-EG", "hi", "fr-CH")

/**
 * Runs this source through the currency name suite and fails the calling test
 * on the first disagreement.
 */
public fun CurrencyNameSource.assertConformsToCurrencyNames(tier: ConformanceTier) {
    // Only the exact tier can require this. A source over Intl answers every
    // lookup and still enumerates nothing, because ECMA-402 has no API to ask
    // what it supports.
    if (tier == ConformanceTier.EXACT) {
        assertTrue(supportedLocales.isNotEmpty(), "a CLDR-backed source is expected to enumerate its locales")
    }

    // The comparison against ICU's own symbols and names is not here: it needs
    // the golden, and the golden lives in the module that owns the table it
    // describes. `currency-cldr-full` runs it as its own case.

    for (tag in FORMAT_LOCALES) {
        val locale = Locale.forLanguageTag(tag)
        for (currency in Currency.entries) {
            assertTrue(symbol(currency, locale).isNotBlank(), "$tag ${currency.code} symbol was blank")
            assertTrue(displayName(currency, locale).isNotBlank(), "$tag ${currency.code} name was blank")
        }
    }

    // CLDR names the currencies people spend, not every code ISO assigns. A
    // handful of special-purpose codes have no name in any locale and fall back
    // to the code, which is the right answer for them. What is worth pinning is
    // that the fallback stays rare, so a table that lost its English names
    // fails here rather than degrading quietly.
    val english = Locale.of("en")
    val unnamed = Currency.active.count { displayName(it, english) == it.code }
    assertTrue(
        unnamed < 10,
        "$unnamed active currencies fell back to their code in en, which suggests the name table is missing rows",
    )
}

/**
 * Runs this source through the currency formatting suite.
 *
 * There is no ICU fixture of formatted output to compare against, so what both
 * tiers check is that what the source printed is what it reads back. That catches
 * a separator, digit or sign the formatter writes and the parser does not
 * recognize, which is the failure mode a table comparison cannot see.
 *
 * The property is stability, not identity, and the difference took a platform
 * source to notice. CLDR formats some currencies with fewer fraction digits than
 * ISO gives them, so HUF prints `0.01` as `HUF 0` and there is no cent left to
 * read back; the amount that survives is the one taken through CLDR's scale. But
 * that is a fact about CLDR, not about currency formatting: `java.util.Currency`
 * gives HUF two fraction digits and round trips `0.01` exactly.
 *
 * So the universal property is that formatting is stable under a round trip:
 * whatever scale a source prints at, re-parsing and re-formatting gives the same
 * string. The stronger CLDR-scale claim is asserted only at
 * [ConformanceTier.EXACT], where it is true by construction.
 */
public fun CurrencyFormatSource.assertConformsToCurrencyFormats(tier: ConformanceTier) {
    // Only the exact tier can require this. A source over Intl answers every
    // lookup and still enumerates nothing, because ECMA-402 has no API to ask
    // what it supports.
    if (tier == ConformanceTier.EXACT) {
        assertTrue(supportedLocales.isNotEmpty(), "a CLDR-backed source is expected to enumerate its locales")
    }

    val amounts = listOf(0L, 1, -1, 50, 123456, -123456, 999999999)
    for (tag in FORMAT_LOCALES) {
        val locale = Locale.forLanguageTag(tag)
        for (code in listOf("USD", "EUR", "JPY", "BHD", "CHF", "HUF", "BRL", "ALL", "CLP")) {
            val currency = Currency.forCodeOrNull(code) ?: continue
            for (minorUnits in amounts) {
                val amount = CurrencyAmount(currency, minorUnits)
                val formatted = format(amount, locale)
                assertTrue(formatted.isNotBlank(), "$tag $code $minorUnits rendered nothing")

                val reread = parseFormattedOrNull(currency, formatted, locale)
                assertNotNull(reread, "$tag $code could not read back '$formatted'")

                // Stable under a second pass, whatever scale this source prints
                // at, except where the amount rounds away to nothing. Formatting
                // -0.01 ALL at zero fraction digits gives "-ALL 0" on Intl and
                // Foundation: the sign is in the text and not in the value, so a
                // second pass drops it. CLDR rounds the sign away too and prints
                // "ALL 0", which is why only a platform source surfaces this.
                val roundsAwayToZero = reread.minorUnits == 0L && minorUnits != 0L
                if (!roundsAwayToZero) {
                    assertEquals(
                        formatted,
                        format(reread, locale),
                        "$tag $code is not stable: '$formatted' re-read and re-printed differently",
                    )
                }

                if (tier == ConformanceTier.EXACT) {
                    val throughCldrScale = currency.cldrToIsoUnits(currency.isoToCldrUnits(minorUnits))
                    assertEquals(
                        CurrencyAmount(currency, throughCldrScale),
                        reread,
                        "$tag $code did not round trip through CLDR's scale via '$formatted'",
                    )
                }
            }
        }
    }

    assertStylesAndVariantsRender(tier)
}

private fun CurrencyFormatSource.assertStylesAndVariantsRender(tier: ConformanceTier) {
    val locale = Locale.of("en")
    val currency = Currency.forCodeOrNull("USD") ?: return
    val amount = CurrencyAmount(currency, -123456)
    for (style in CurrencySymbolStyle.entries) {
        for (signDisplay in SignDisplay.entries) {
            for (cash in listOf(false, true)) {
                val formatted = format(amount, locale, style, signDisplay, cash)
                assertTrue(formatted.isNotBlank(), "$style $signDisplay cash=$cash rendered nothing")
            }
        }
    }
    if (tier != ConformanceTier.EXACT) return
    // The accounting pattern in en wraps negatives in parentheses rather than
    // writing a minus, which is the one place the two patterns visibly differ.
    assertTrue(
        format(amount, locale, CurrencySymbolStyle.SYMBOL, SignDisplay.ACCOUNTING).startsWith("("),
        "en accounting negatives are parenthesized",
    )
}
