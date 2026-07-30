package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.cldr.displayName
import dev.carcara.kotlinx.locale.currency.cldr.symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurrencyNamesTest {

    @Test
    fun localizesSymbolsAndNames() {
        assertEquals("$", Currency.USD.symbol(Locale.forLanguageTag("en")))
        assertEquals("US$", Currency.USD.symbol(Locale.forLanguageTag("pt-BR")))
        assertEquals("¥", Currency.JPY.symbol(Locale.forLanguageTag("en")))
        assertEquals("￥", Currency.JPY.symbol(Locale.forLanguageTag("ja")))
        assertEquals("₹", Currency.INR.symbol(Locale.forLanguageTag("hi")))
        assertEquals("€", Currency.EUR.symbol(Locale.forLanguageTag("de")))
        // No CLDR symbol -> the ISO code.
        assertEquals("CHF", Currency.CHF.symbol(Locale.forLanguageTag("de-CH")))

        assertEquals("US Dollar", Currency.USD.displayName(Locale.forLanguageTag("en")))
        assertEquals("Dólar americano", Currency.USD.displayName(Locale.forLanguageTag("pt-BR")))
        assertEquals("Schweizer Franken", Currency.CHF.displayName(Locale.forLanguageTag("de-CH")))
        assertEquals("euro", Currency.EUR.displayName(Locale.forLanguageTag("es")))
        assertEquals("日本円", Currency.JPY.displayName(Locale.forLanguageTag("ja")))
    }

    @Test
    fun everyCurrencyResolvesSymbolAndNameInMajorLocales() {
        val locales = listOf("en", "de", "ja", "pt-BR", "ar-EG").map(Locale::forLanguageTag)
        for (locale in locales) {
            for (currency in Currency.entries) {
                assertTrue(currency.symbol(locale).isNotBlank(), "$locale ${currency.code} symbol")
                assertTrue(currency.displayName(locale).isNotBlank(), "$locale ${currency.code} name")
            }
        }
    }

    @Test
    fun reportsTheLocalesItCarriesDataFor() {
        val tags = CldrCurrency.supportedLocales.map(Locale::toLanguageTag)
        assertTrue(tags.size > 700, "expected hundreds of locales, got ${tags.size}")
        assertTrue("en" in tags)
        assertTrue("pt-BR" in tags)
        assertTrue("zh-Hant" in tags)
        assertTrue("root" !in tags)
    }
}
