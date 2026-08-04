package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.cldr.format
import dev.carcara.kotlinx.locale.currency.cldr.parseFormatted
import dev.carcara.kotlinx.locale.currency.cldr.parseFormattedOrNull
import dev.carcara.kotlinx.locale.number.SignDisplay
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
    fun readsTheCurrencyOutOfTheTextWhenNotToldWhichItIs() {
        val ptBr = locale("pt-BR")
        assertEquals(
            CurrencyAmount(Currency.USD, 123456),
            CurrencyAmount.parseFormattedOrNull("US$ 1.234,56", ptBr),
        )
        assertEquals(
            CurrencyAmount(Currency.BRL, 123456),
            CurrencyAmount.parseFormattedOrNull("R$ 1.234,56", ptBr),
        )
        assertEquals(
            CurrencyAmount(Currency.USD, 123456),
            CurrencyAmount.parseFormattedOrNull("$1,234.56", locale("en")),
        )
        // The symbol a locale actually prints, read back without being told the
        // currency: hu writes 200 forints as "200 Ft".
        val hu = locale("hu")
        assertEquals(
            CurrencyAmount(Currency.HUF, 20000),
            CurrencyAmount.parseFormattedOrNull(CurrencyAmount(Currency.HUF, 20000).format(hu), hu),
        )
        // The same with an ordinary space where the formatter writes U+00A0.
        assertEquals(CurrencyAmount(Currency.HUF, 20000), CurrencyAmount.parseFormattedOrNull("200 Ft", hu))
        // And the ISO code names a currency on its own, the way ICU registers it
        // in the parse table alongside the symbol.
        assertEquals(CurrencyAmount(Currency.HUF, 20000), CurrencyAmount.parseFormattedOrNull("HUF 200", hu))
    }

    @Test
    fun everyLocaleReadsItsOwnCurrencyOutputBackWithoutBeingTold() {
        // The round trip that matters: whatever the formatter prints, the
        // currency-less parse identifies. Run over the currencies that have a
        // symbol of their own in each locale, since the rest print an ISO code
        // and are the easy case.
        for (tag in listOf("en", "hu", "pt-BR", "de", "ja", "da", "en-ZA")) {
            val locale = locale(tag)
            for (currency in Currency.entries) {
                if (CldrCurrency.currencySymbolOrNull(currency.code, locale) == null) continue
                val amount = CurrencyAmount(currency, 123456)
                val printed = amount.format(locale)
                val read = CurrencyAmount.parseFormattedOrNull(printed, locale)
                assertEquals(currency, read?.currency, "$tag ${currency.code} printed '$printed'")
            }
        }
    }

    @Test
    fun refusesToIdentifyACurrencyFromASpellingThatNamesMoreThanOne() {
        // In pt-BR no currency's plain symbol is "$": USD is US$ and BRL is R$.
        // "$" is the narrow spelling, which names many currencies and so
        // identifies none of them.
        assertNull(CurrencyAmount.parseFormattedOrNull("$ 1.234,56", locale("pt-BR")))
        // Nothing currency-like at all.
        assertNull(CurrencyAmount.parseFormattedOrNull("1.234,56", locale("pt-BR")))
        // Told which currency it is, the same text reads back.
        assertEquals(
            123456,
            parsed(Currency.USD, "$ 1.234,56", "pt-BR"),
        )
    }

    @Test
    fun everyLocaleSpellsItsCurrenciesApart() {
        // The property the currency-less parse rests on: within one locale, no
        // two currencies share a plain or variant spelling. Where that failed,
        // the index would drop the string and identification would start
        // answering null for text that reads unambiguously to a person.
        for (tag in listOf("en", "en-CA", "en-AU", "pt-BR", "de", "de-CH", "ja", "es-MX", "ar-EG")) {
            val locale = locale(tag)
            val seen = HashMap<String, String>()
            for (currency in Currency.entries) {
                for (style in listOf(CurrencySymbolStyle.SYMBOL, CurrencySymbolStyle.VARIANT_SYMBOL)) {
                    val spelling = CldrCurrency.currencySymbolOrNull(currency.code, locale, style) ?: continue
                    val owner = seen.put(spelling, currency.code)
                    if (owner != null && owner != currency.code) {
                        throw AssertionError("$tag spells both $owner and ${currency.code} as '$spelling'")
                    }
                }
            }
        }
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
                val formatted = CurrencyAmount(Currency.USD, minorUnits).format(locale, signDisplay = SignDisplay.ACCOUNTING)
                assertEquals(
                    minorUnits,
                    CurrencyAmount.parseFormattedOrNull(Currency.USD, formatted, locale)?.minorUnits,
                    "$tag '$formatted'",
                )
            }
        }
    }
}
