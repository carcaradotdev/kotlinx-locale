package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.cldr.format
import dev.carcara.kotlinx.locale.currency.cldr.parseFormatted
import dev.carcara.kotlinx.locale.currency.cldr.parseFormattedOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CurrencyParseFormattedTest {

    private fun locale(tag: String) = Locale.forLanguageTag(tag)

    private fun parsed(currency: Currency, text: String, tag: String): Long? =
        CurrencyAmount.parseFormattedOrNull(currency, text, locale(tag))?.minorUnits

    @Test
    fun parsesLocalizedFormats() {
        assertEquals(20000, parsed(Currency.BRL, "R$ 200,00", "pt-BR"))
        assertEquals(123456, parsed(Currency.BRL, "R$ 1.234,56", "pt-BR"))
        assertEquals(123456, parsed(Currency.USD, "$1,234.56", "en"))
        assertEquals(123456, parsed(Currency.EUR, "1.234,56 €", "de"))
        assertEquals(1234, parsed(Currency.JPY, "￥1,234", "ja"))
    }

    @Test
    fun parsedAmountsAreIsoMinorUnitsEvenWhenCldrDropsDecimals() {
        // HUF: CLDR formats 0 digits, ISO defines 2. "200 Ft" is 200.00 forints.
        assertEquals(20000, parsed(Currency.HUF, "200 Ft", "hu"))
        assertEquals(20000, parsed(Currency.HUF, "200", "hu"))
        // An explicit fraction is accepted as long as ISO can represent it.
        assertEquals(20050, parsed(Currency.HUF, "200,50 Ft", "hu"))
        // Same for the Albanian lek.
        assertEquals(12300, parsed(Currency.ALL, "ALL 123", "en"))
    }

    @Test
    fun acceptsSymbolCodeNameOrBareNumbers() {
        assertEquals(123456, parsed(Currency.USD, "USD 1,234.56", "en"))
        assertEquals(123456, parsed(Currency.USD, "1,234.56", "en"))
        assertEquals(123456, parsed(Currency.USD, "US Dollar 1,234.56", "en"))
        assertEquals(50, parsed(Currency.USD, "usd .50", "en"))
    }

    @Test
    fun recognizesNegativeForms() {
        assertEquals(-123456, parsed(Currency.USD, "-$1,234.56", "en"))
        assertEquals(-123456, parsed(Currency.USD, "($1,234.56)", "en"))
        assertEquals(-123456, parsed(Currency.EUR, "-1.234,56 €", "de"))
        assertEquals(-123456, parsed(Currency.EUR, "€ -1.234,56", "nl"))
        assertEquals(-123456, parsed(Currency.CHF, "CHF-1'234.56", "de-CH"))
        assertEquals(-5, parsed(Currency.USD, "-$0.05", "en"))
    }

    @Test
    fun readsSeparatorsPerLocale() {
        // In de a dot groups; in en it separates decimals.
        assertEquals(123400, parsed(Currency.EUR, "1.234", "de"))
        assertEquals(123, parsed(Currency.EUR, "1.23", "en"))
        assertEquals(123456, parsed(Currency.CHF, "CHF 1'234.56", "de-CH"))
    }

    @Test
    fun parsesNativeDigits() {
        val formatted = CurrencyAmount(Currency.EGP, 123456).format(locale("ar-EG"))
        assertEquals(123456, parsed(Currency.EGP, formatted, "ar-EG"))
        assertEquals(123456, parsed(Currency.EGP, "١٬٢٣٤٫٥٦", "ar-EG"))
    }

    @Test
    fun rejectsUnparseableText() {
        assertNull(parsed(Currency.USD, "", "en"))
        assertNull(parsed(Currency.USD, "abc", "en"))
        assertNull(parsed(Currency.USD, "$", "en"))
        assertNull(parsed(Currency.USD, "12x3", "en"))
        assertNull(parsed(Currency.USD, "1.2.3", "en"))
        assertNull(parsed(Currency.USD, "12.", "en"))
        assertNull(parsed(Currency.USD, "1,234.567", "en"))
        // JPY has no ISO minor units: a non-zero fraction cannot be represented.
        assertNull(parsed(Currency.JPY, "5.5", "en"))
        assertEquals(5, parsed(Currency.JPY, "5.0", "en"))
        assertFailsWith<IllegalArgumentException> {
            CurrencyAmount.parseFormatted(Currency.USD, "abc", locale("en"))
        }
    }

    @Test
    fun formattedOutputRoundTripsInEveryLocale() {
        val currencies = listOf(
            Currency.USD,
            Currency.EUR,
            Currency.JPY,
            Currency.BHD,
            Currency.HUF,
            Currency.ALL,
        )
        val samples = listOf(0L, 5, -1, 100, 123456, -9999999)
        for (locale in CldrCurrency.supportedLocales) {
            for (currency in currencies) {
                for (minorUnits in samples) {
                    // Formatting rounds to CLDR digits, so the expected value is
                    // the ISO amount after the CLDR round trip.
                    val expected = currency.cldrToIsoUnits(currency.isoToCldrUnits(minorUnits))
                    for (style in CurrencySymbolStyle.entries) {
                        val formatted = CurrencyAmount(currency, minorUnits).format(locale, style)
                        assertEquals(
                            expected,
                            CurrencyAmount.parseFormattedOrNull(currency, formatted, locale)?.minorUnits,
                            "$locale ${currency.code} $style '$formatted'",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun accountingOutputRoundTrips() {
        for (tag in listOf("en", "de", "pt-BR", "ja", "ar-EG", "hu", "nl", "de-CH")) {
            val locale = locale(tag)
            for (minorUnits in listOf(-123456L, -1)) {
                val formatted = CurrencyAmount(Currency.USD, minorUnits).format(locale, accounting = true)
                assertEquals(
                    minorUnits,
                    CurrencyAmount.parseFormattedOrNull(Currency.USD, formatted, locale)?.minorUnits,
                    "$tag '$formatted'",
                )
            }
        }
    }
}
