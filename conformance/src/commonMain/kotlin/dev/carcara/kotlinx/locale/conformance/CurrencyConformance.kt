package dev.carcara.kotlinx.locale.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.CurrencyFormatSource
import dev.carcara.kotlinx.locale.currency.CurrencyNameSource
import dev.carcara.kotlinx.locale.currency.CurrencySymbolStyle
import dev.carcara.kotlinx.locale.currency.cldrToIsoUnits
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.currency.displayName
import dev.carcara.kotlinx.locale.currency.forCodeOrNull
import dev.carcara.kotlinx.locale.currency.format
import dev.carcara.kotlinx.locale.currency.isoToCldrUnits
import dev.carcara.kotlinx.locale.currency.parseFormattedOrNull
import dev.carcara.kotlinx.locale.currency.symbol
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The locales the formatting checks run over, chosen for separator and digit variety. */
private val FORMAT_LOCALES = listOf("en", "de", "ja", "pt-BR", "ar-EG", "hi", "fr-CH")

/**
 * Runs this source through the currency name suite and fails the calling test
 * on the first disagreement.
 */
public fun CurrencyNameSource.assertConformsToCurrencyNames(tier: ConformanceTier) {
    assertTrue(supportedLocales.isNotEmpty(), "a source that supports no locale answers nothing")

    if (tier == ConformanceTier.EXACT) assertMatchesIcuCurrencyNames()

    for (tag in FORMAT_LOCALES) {
        val locale = Locale.forLanguageTag(tag)
        for (currency in Currency.entries) {
            assertTrue(symbol(currency, locale).isNotBlank(), "$tag ${currency.code} symbol was blank")
            assertTrue(displayName(currency, locale).isNotBlank(), "$tag ${currency.code} name was blank")
        }
    }
}

private fun CurrencyNameSource.assertMatchesIcuCurrencyNames() {
    assertTrue(icuCurrencyGoldenData.size >= 25, "expected the full golden locale set")
    for (golden in icuCurrencyGoldenData) {
        val locale = Locale.forLanguageTag(golden.tag)
        for ((code, icuSymbol) in golden.symbols) {
            val currency = assertNotNull(Currency.forCodeOrNull(code), "$code is not in this build's entry set")
            assertEquals(
                icuSymbol.normalizedSpaces(),
                symbol(currency, locale).normalizedSpaces(),
                "${golden.tag} $code symbol",
            )
        }
        for ((code, icuName) in golden.names) {
            val currency = assertNotNull(Currency.forCodeOrNull(code), "$code is not in this build's entry set")
            assertEquals(
                icuName.normalizedSpaces(),
                displayName(currency, locale).normalizedSpaces(),
                "${golden.tag} $code name",
            )
        }
    }
}

/**
 * Runs this source through the currency formatting suite.
 *
 * There is no ICU fixture of formatted output to compare against, so both tiers
 * check the same property: what the source printed is what it reads back. That
 * is the strong check, because it catches a separator, digit or sign the
 * formatter writes and the parser does not recognize — the failure mode a
 * table comparison cannot see.
 *
 * The round trip is not the identity, and cannot be. CLDR formats some
 * currencies with fewer fraction digits than ISO gives them, so HUF prints
 * `0.01` as `HUF 0` and there is no cent left to read back. What survives is
 * the amount taken through CLDR's scale, which is what
 * [CurrencyAmount.format] documents it prints.
 */
public fun CurrencyFormatSource.assertConformsToCurrencyFormats(tier: ConformanceTier) {
    assertTrue(supportedLocales.isNotEmpty(), "a source that supports no locale answers nothing")

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
                val throughCldrScale = currency.cldrToIsoUnits(currency.isoToCldrUnits(minorUnits))
                assertEquals(
                    CurrencyAmount(currency, throughCldrScale),
                    reread,
                    "$tag $code did not round trip through '$formatted'",
                )
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
        for (accounting in listOf(false, true)) {
            for (cash in listOf(false, true)) {
                val formatted = format(amount, locale, style, accounting, cash)
                assertTrue(formatted.isNotBlank(), "$style accounting=$accounting cash=$cash rendered nothing")
            }
        }
    }
    if (tier != ConformanceTier.EXACT) return
    // The accounting pattern in en wraps negatives in parentheses rather than
    // writing a minus, which is the one place the two patterns visibly differ.
    assertTrue(
        format(amount, locale, CurrencySymbolStyle.SYMBOL, accounting = true, cash = false).startsWith("("),
        "en accounting negatives are parenthesized",
    )
}

/**
 * Cross-checks this build's ISO 4217 numeric codes against ICU's independently
 * maintained table. A property of the entry set rather than of any source, so
 * it takes no receiver.
 */
public fun assertCurrencyNumericCodesMatchIcu() {
    assertTrue(icuCurrencyNumericCodes.size > 250, "expected ICU's full numeric code table")
    var checked = 0
    for (currency in Currency.entries) {
        val icuNumeric = icuCurrencyNumericCodes[currency.code] ?: continue
        assertEquals(icuNumeric, currency.numericCode, "${currency.code} numeric code")
        checked++
    }
    assertTrue(checked > 150, "expected to check the active ISO set, checked $checked")
}
