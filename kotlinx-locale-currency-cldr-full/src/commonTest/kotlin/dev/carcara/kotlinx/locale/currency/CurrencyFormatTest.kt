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

package dev.carcara.kotlinx.locale.currency

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.cldr.displayName
import dev.carcara.kotlinx.locale.currency.cldr.format
import dev.carcara.kotlinx.locale.currency.cldr.symbol
import dev.carcara.kotlinx.locale.number.SignDisplay
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * Golden expectations taken from CLDR release-48-2 data. Several locales
 * separate the symbol from the number with U+00A0 (NBSP), written as an
 * escape below to keep it visible.
 */
val CurrencyFormatTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    fun locale(tag: String) = Locale.forLanguageTag(tag)

    fun format(
        currency: Currency,
        minorUnits: Long,
        tag: String,
        style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL,
        signDisplay: SignDisplay = SignDisplay.AUTO,
        cash: Boolean = false,
    ): String = CurrencyAmount(currency, minorUnits).format(locale(tag), style, signDisplay, cash)

    test("formatsEnglish") {
        assertEquals("$1,234.56", format(Currency.USD, 123456, "en"))
        assertEquals("-$1,234.56", format(Currency.USD, -123456, "en"))
        assertEquals("$0.05", format(Currency.USD, 5, "en"))
        assertEquals("¥1,234", format(Currency.JPY, 1234, "en"))
    }

    test("formatsTheAccountingPattern") {
        assertEquals("($1,234.56)", format(Currency.USD, -123456, "en", signDisplay = SignDisplay.ACCOUNTING))
        assertEquals("$1,234.56", format(Currency.USD, 123456, "en", signDisplay = SignDisplay.ACCOUNTING))
    }

    test("formatsWithIsoCodes") {
        // The alphaNextToNumber pattern inserts a NBSP after the alphabetic code.
        assertEquals("USD\u00A01,234.56", format(Currency.USD, 123456, "en", style = CurrencySymbolStyle.CODE))
        assertEquals("JPY\u00A01,234", format(Currency.JPY, 1234, "ja", style = CurrencySymbolStyle.CODE))
    }

    test("usesTheAlphaPatternForAlphabeticSymbols") {
        // CHF has no symbol, so the symbol is the code, which is alphabetic.
        assertEquals("CHF\u00A01,234.56", format(Currency.CHF, 123456, "en"))
        assertEquals("ALL\u00A0123", format(Currency.ALL, 12345, "en"))
    }

    test("formatsGermanStyleSuffixSymbols") {
        assertEquals("1.234,56\u00A0€", format(Currency.EUR, 123456, "de"))
        assertEquals("-1.234,56\u00A0€", format(Currency.EUR, -123456, "de"))
    }

    test("formatsPortuguese") {
        assertEquals("R$\u00A01.234,56", format(Currency.BRL, 123456, "pt-BR"))
        assertEquals("US$\u00A01.234,56", format(Currency.USD, 123456, "pt"))
    }

    test("formatsSwissNegativeSubpattern") {
        assertEquals("CHF\u00A01'234.56", format(Currency.CHF, 123456, "de-CH"))
        assertEquals("CHF-1'234.56", format(Currency.CHF, -123456, "de-CH"))
    }

    test("formatsDutchNegativeSubpattern") {
        assertEquals("€\u00A01.234,56", format(Currency.EUR, 123456, "nl"))
        assertEquals("€\u00A0-1.234,56", format(Currency.EUR, -123456, "nl"))
    }

    test("formatsIndianLakhGrouping") {
        assertEquals("₹1,23,456.78", format(Currency.INR, 12345678, "hi"))
        assertEquals("₹123.45", format(Currency.INR, 12345, "hi"))
    }

    test("honorsMinimumGroupingDigits") {
        // es requires two digits before the first separator: 1000 stays ungrouped.
        assertEquals("1000,00\u00A0€", format(Currency.EUR, 100000, "es"))
        assertEquals("10.000,00\u00A0€", format(Currency.EUR, 1000000, "es"))
    }

    test("formatsCashWithRoundingIncrements") {
        // CHF cash rounds to 0.05.
        assertEquals("CHF\u00A010.00", format(Currency.CHF, 1002, "en", cash = true))
        assertEquals("CHF\u00A010.05", format(Currency.CHF, 1003, "en", cash = true))
        // AMD cash drops the fraction digits entirely.
        assertEquals("AMD\u00A0124", format(Currency.AMD, 12350, "en", cash = true))
    }

    test("formatsCldrDigitsThatDivergeFromIso") {
        // ALL: ISO carries 2 minor units, CLDR formats none (half-even rescale).
        assertEquals("ALL\u00A0123", format(Currency.ALL, 12345, "en"))
        assertEquals("ALL\u00A0124", format(Currency.ALL, 12350, "en"))
        // BHD keeps its 3 ISO digits.
        assertEquals("BHD\u00A01,234.567", format(Currency.BHD, 1234567, "en"))
        // XAU has no ISO minor units but CLDR renders 2 digits.
        assertEquals("XAU\u00A05.00", format(Currency.XAU, 5, "en"))
    }

    test("formatsArabicWithNativeDigits") {
        val formatted = format(Currency.EGP, 123456, "ar-EG")
        assertTrue("١٬٢٣٤٫٥٦" in formatted, "expected Arabic-Indic digits in '$formatted'")
    }

    test("fallsBackToRootForUnknownLocales") {
        assertEquals("US$\u00A01,234.56", format(Currency.USD, 123456, "xx"))
    }

    test("formatsZeroAndSignEdgeCases") {
        assertEquals("$0.00", format(Currency.USD, 0, "en"))
        assertEquals("-$0.01", format(Currency.USD, -1, "en"))
        // -0.40 lekes rounds to zero at CLDR's 0 digits and keeps its sign.
        // That is what SignDisplay documents for every value that does not
        // name negative zero, and what ICU and Intl.NumberFormat both write.
        // An unsigned zero is what SignDisplay.NEGATIVE is for.
        assertEquals("-ALL\u00A00", format(Currency.ALL, -40, "en"))
        assertEquals("ALL\u00A00", format(Currency.ALL, -40, "en", signDisplay = SignDisplay.NEGATIVE))
    }

    test("keepsTheSignOnAnAmountThatRoundsAwayUnderEveryOption") {
        // The rounding that produced the zero has to survive: a Swiss franc cash
        // amount of -0.02 rounds to the nearest 0.05, so it prints as -0.00
        // rather than as the -0.02 it started from.
        assertEquals("-CHF\u00A00.00", format(Currency.CHF, -2, "en", cash = true))
        assertEquals("-CHF\u00A00.05", format(Currency.CHF, -3, "en", cash = true))
        // A digit-count override is applied after CLDR's scale, so the forint
        // has already rounded away by the time three digits are asked for.
        assertEquals(
            "-HUF\u00A00.000",
            CurrencyAmount(Currency.HUF, -1).format(locale("en"), fractionDigits = 3),
        )
        // Which sign the zero carries is SignDisplay's to decide, not this
        // layer's. It only has to stop throwing the information away.
        assertEquals("-HUF\u00A00", format(Currency.HUF, -1, "en", signDisplay = SignDisplay.AUTO))
        assertEquals("-HUF\u00A00", format(Currency.HUF, -1, "en", signDisplay = SignDisplay.ALWAYS))
        assertEquals("HUF\u00A00", format(Currency.HUF, -1, "en", signDisplay = SignDisplay.NEVER))
        assertEquals("HUF\u00A00", format(Currency.HUF, -1, "en", signDisplay = SignDisplay.NEGATIVE))
        assertEquals("HUF\u00A00", format(Currency.HUF, -1, "en", signDisplay = SignDisplay.EXCEPT_ZERO))
        assertEquals("(HUF\u00A00)", format(Currency.HUF, -1, "en", signDisplay = SignDisplay.ACCOUNTING))
        assertEquals("HUF\u00A00", format(Currency.HUF, -1, "en", signDisplay = SignDisplay.ACCOUNTING_NEGATIVE))
        // A positive that rounds away carries no sign either way.
        assertEquals("HUF\u00A00", format(Currency.HUF, 1, "en"))
    }

    test("formatsLongExtremes") {
        assertEquals("$92,233,720,368,547,758.07", format(Currency.USD, Long.MAX_VALUE, "en"))
        assertEquals("-$92,233,720,368,547,758.08", format(Currency.USD, Long.MIN_VALUE, "en"))
        assertEquals("¥9,223,372,036,854,775,807", format(Currency.JPY, Long.MAX_VALUE, "en"))
    }

    test("formatsLargeAmountsWithSecondaryGrouping") {
        assertEquals("₹1,23,45,67,890.12", format(Currency.INR, 123456789012, "hi"))
        assertEquals("$92,23,37,20,36,85,475.80", format(Currency.USD, 92233720368547580, "hi"))
    }

    test("formatsNegativeCashAmounts") {
        assertEquals("-CHF\u00A010.05", format(Currency.CHF, -1003, "en", cash = true))
        assertEquals("(CHF\u00A010.05)", format(Currency.CHF, -1003, "en", signDisplay = SignDisplay.ACCOUNTING, cash = true))
    }

    test("nativeDigitLocalesNeverLeakAsciiDigits") {
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

    test("everyLocaleDistinguishesNegativeAmounts") {
        for (locale in CldrCurrency.supportedLocales) {
            val positive = CurrencyAmount(Currency.USD, 123456).format(locale)
            val negative = CurrencyAmount(Currency.USD, -123456).format(locale)
            assertTrue(positive != negative, "$locale renders -1234.56 like 1234.56")
        }
    }

    test("everyLocaleFormatsEveryStyleWithoutBlanks") {
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
                assertTrue(amount.format(locale, signDisplay = SignDisplay.ACCOUNTING).isNotBlank())
                assertTrue(amount.format(locale, cash = true).isNotBlank())
            }
        }
    }

    test("everyLocaleLocalizesSymbolsAndNames") {
        for (locale in CldrCurrency.supportedLocales) {
            for (currency in listOf(Currency.USD, Currency.EUR)) {
                assertTrue(currency.symbol(locale).isNotBlank(), "$locale ${currency.code} symbol")
                assertTrue(currency.displayName(locale).isNotBlank(), "$locale ${currency.code} name")
            }
        }
    }
}
