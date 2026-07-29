package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.cldr.displayName
import dev.carcara.kotlinx.locale.currency.cldr.format
import dev.carcara.kotlinx.locale.currency.cldr.symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden expectations taken from CLDR release-48-2 data. Several locales
 * separate the symbol from the number with U+00A0 (NBSP), written as an
 * escape below to keep it visible.
 */
class CurrencyFormatTest {

    private fun locale(tag: String) = Locale.forLanguageTag(tag)

    private fun format(
        currency: Currency,
        minorUnits: Long,
        tag: String,
        style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL,
        accounting: Boolean = false,
        cash: Boolean = false,
    ): String = CurrencyAmount(currency, minorUnits).format(locale(tag), style, accounting, cash)

    @Test
    fun formatsEnglish() {
        assertEquals("$1,234.56", format(Currency.USD, 123456, "en"))
        assertEquals("-$1,234.56", format(Currency.USD, -123456, "en"))
        assertEquals("$0.05", format(Currency.USD, 5, "en"))
        assertEquals("¥1,234", format(Currency.JPY, 1234, "en"))
    }

    @Test
    fun formatsTheAccountingPattern() {
        assertEquals("($1,234.56)", format(Currency.USD, -123456, "en", accounting = true))
        assertEquals("$1,234.56", format(Currency.USD, 123456, "en", accounting = true))
    }

    @Test
    fun formatsWithIsoCodes() {
        // The alphaNextToNumber pattern inserts a NBSP after the alphabetic code.
        assertEquals("USD\u00A01,234.56", format(Currency.USD, 123456, "en", style = CurrencySymbolStyle.CODE))
        assertEquals("JPY\u00A01,234", format(Currency.JPY, 1234, "ja", style = CurrencySymbolStyle.CODE))
    }

    @Test
    fun usesTheAlphaPatternForAlphabeticSymbols() {
        // CHF has no symbol, so the symbol is the code, which is alphabetic.
        assertEquals("CHF\u00A01,234.56", format(Currency.CHF, 123456, "en"))
        assertEquals("ALL\u00A0123", format(Currency.ALL, 12345, "en"))
    }

    @Test
    fun formatsGermanStyleSuffixSymbols() {
        assertEquals("1.234,56\u00A0€", format(Currency.EUR, 123456, "de"))
        assertEquals("-1.234,56\u00A0€", format(Currency.EUR, -123456, "de"))
    }

    @Test
    fun formatsPortuguese() {
        assertEquals("R$\u00A01.234,56", format(Currency.BRL, 123456, "pt-BR"))
        assertEquals("US$\u00A01.234,56", format(Currency.USD, 123456, "pt"))
    }

    @Test
    fun formatsSwissNegativeSubpattern() {
        assertEquals("CHF\u00A01'234.56", format(Currency.CHF, 123456, "de-CH"))
        assertEquals("CHF-1'234.56", format(Currency.CHF, -123456, "de-CH"))
    }

    @Test
    fun formatsDutchNegativeSubpattern() {
        assertEquals("€\u00A01.234,56", format(Currency.EUR, 123456, "nl"))
        assertEquals("€\u00A0-1.234,56", format(Currency.EUR, -123456, "nl"))
    }

    @Test
    fun formatsIndianLakhGrouping() {
        assertEquals("₹1,23,456.78", format(Currency.INR, 12345678, "hi"))
        assertEquals("₹123.45", format(Currency.INR, 12345, "hi"))
    }

    @Test
    fun honorsMinimumGroupingDigits() {
        // es requires two digits before the first separator: 1000 stays ungrouped.
        assertEquals("1000,00\u00A0€", format(Currency.EUR, 100000, "es"))
        assertEquals("10.000,00\u00A0€", format(Currency.EUR, 1000000, "es"))
    }

    @Test
    fun formatsCashWithRoundingIncrements() {
        // CHF cash rounds to 0.05.
        assertEquals("CHF\u00A010.00", format(Currency.CHF, 1002, "en", cash = true))
        assertEquals("CHF\u00A010.05", format(Currency.CHF, 1003, "en", cash = true))
        // AMD cash drops the fraction digits entirely.
        assertEquals("AMD\u00A0124", format(Currency.AMD, 12350, "en", cash = true))
    }

    @Test
    fun formatsCldrDigitsThatDivergeFromIso() {
        // ALL: ISO carries 2 minor units, CLDR formats none (half-even rescale).
        assertEquals("ALL\u00A0123", format(Currency.ALL, 12345, "en"))
        assertEquals("ALL\u00A0124", format(Currency.ALL, 12350, "en"))
        // BHD keeps its 3 ISO digits.
        assertEquals("BHD\u00A01,234.567", format(Currency.BHD, 1234567, "en"))
        // XAU has no ISO minor units but CLDR renders 2 digits.
        assertEquals("XAU\u00A05.00", format(Currency.XAU, 5, "en"))
    }

    @Test
    fun formatsArabicWithNativeDigits() {
        val formatted = format(Currency.EGP, 123456, "ar-EG")
        assertTrue("١٬٢٣٤٫٥٦" in formatted, "expected Arabic-Indic digits in '$formatted'")
    }

    @Test
    fun fallsBackToRootForUnknownLocales() {
        assertEquals("US$\u00A01,234.56", format(Currency.USD, 123456, "xx"))
    }

    @Test
    fun formatsZeroAndSignEdgeCases() {
        assertEquals("$0.00", format(Currency.USD, 0, "en"))
        assertEquals("-$0.01", format(Currency.USD, -1, "en"))
        // -0.40 lekë rounds to zero at CLDR's 0 digits: no minus sign survives.
        assertEquals("ALL\u00A00", format(Currency.ALL, -40, "en"))
    }

    @Test
    fun formatsLongExtremes() {
        assertEquals("$92,233,720,368,547,758.07", format(Currency.USD, Long.MAX_VALUE, "en"))
        assertEquals("-$92,233,720,368,547,758.08", format(Currency.USD, Long.MIN_VALUE, "en"))
        assertEquals("¥9,223,372,036,854,775,807", format(Currency.JPY, Long.MAX_VALUE, "en"))
    }

    @Test
    fun formatsLargeAmountsWithSecondaryGrouping() {
        assertEquals("₹1,23,45,67,890.12", format(Currency.INR, 123456789012, "hi"))
        assertEquals("$92,23,37,20,36,85,475.80", format(Currency.USD, 92233720368547580, "hi"))
    }

    @Test
    fun formatsNegativeCashAmounts() {
        assertEquals("-CHF\u00A010.05", format(Currency.CHF, -1003, "en", cash = true))
        assertEquals("(CHF\u00A010.05)", format(Currency.CHF, -1003, "en", accounting = true, cash = true))
    }

    @Test
    fun nativeDigitLocalesNeverLeakAsciiDigits() {
        for (tag in listOf("ar-EG", "fa", "bn")) {
            for (currency in listOf(Currency.USD, Currency.JPY, Currency.BHD)) {
                val formatted = format(currency, 1234567, tag)
                assertTrue(
                    formatted.none { it in '0'..'9' },
                    "$tag ${currency.code} leaked ASCII digits: '$formatted'",
                )
            }
        }
    }

    @Test
    fun everyLocaleDistinguishesNegativeAmounts() {
        for (locale in CldrCurrency.supportedLocales) {
            val positive = CurrencyAmount(Currency.USD, 123456).format(locale)
            val negative = CurrencyAmount(Currency.USD, -123456).format(locale)
            assertTrue(positive != negative, "$locale renders -1234.56 like 1234.56")
        }
    }

    @Test
    fun everyLocaleFormatsEveryStyleWithoutBlanks() {
        val currencies = listOf(Currency.USD, Currency.EUR, Currency.JPY, Currency.BHD)
        for (locale in CldrCurrency.supportedLocales) {
            for (currency in currencies) {
                val amount = CurrencyAmount(currency, 123456)
                for (style in CurrencySymbolStyle.entries) {
                    val formatted = amount.format(locale, style)
                    assertTrue(formatted.isNotBlank(), "$locale ${currency.code} $style was blank")
                    if (style == CurrencySymbolStyle.CODE) {
                        assertTrue(
                            currency.code in formatted,
                            "$locale ${currency.code} missing code in '$formatted'",
                        )
                    }
                }
                assertTrue(amount.format(locale, accounting = true).isNotBlank())
                assertTrue(amount.format(locale, cash = true).isNotBlank())
            }
        }
    }

    @Test
    fun everyLocaleLocalizesSymbolsAndNames() {
        for (locale in CldrCurrency.supportedLocales) {
            for (currency in listOf(Currency.USD, Currency.EUR)) {
                assertTrue(currency.symbol(locale).isNotBlank(), "$locale ${currency.code} symbol")
                assertTrue(currency.displayName(locale).isNotBlank(), "$locale ${currency.code} name")
            }
        }
    }
}
