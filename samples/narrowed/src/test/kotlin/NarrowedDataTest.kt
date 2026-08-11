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

import com.example.locale.GeneratedCountryNames
import com.example.locale.catalog.JA
import com.example.locale.catalog.PT
import com.example.locale.displayName
import com.example.locale.format
import com.example.locale.formatPluralName
import com.example.locale.pluralName
import com.example.locale.symbol
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.country.forAlpha2
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.forCode
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.toLocale
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a narrowed build gets, and what it does not.
 *
 * The imports are the point: `com.example.locale` where a full build would say
 * `dev.carcara.kotlinx.locale.country.cldr`, and everything after that reads the
 * same. That is the claim this test exists to check.
 */
class NarrowedDataTest {

    private val ptBr = Locale.forLanguageTag("pt-BR")
    private val en = Locale.forLanguageTag("en")
    private val ja = Locale.forLanguageTag("ja")

    @Test
    fun `answers for the locales this build generated`() {
        assertEquals("Brasil", Country.BR.displayName(ptBr))
        assertEquals("Brazil", Country.BR.displayName(en))
        assertEquals("ブラジル", Country.BR.displayName(ja))
    }

    @Test
    fun `falls back to the configured locale for anything else`() {
        // German was not generated, so it resolves through the fallback rather
        // than returning nothing. A full build would say "Brasilien".
        val german = Locale.forLanguageTag("de")
        assertEquals("Brazil", Country.BR.displayName(german))
    }

    @Test
    fun `reports only the locales it carries`() {
        val tags = GeneratedCountryNames.supportedLocales.map(Locale::toLanguageTag).toSet()
        assertTrue("pt-BR" in tags, "the locale set is $tags")
        assertTrue("en" in tags, "the locale set is $tags")
        assertTrue("ja" in tags, "the locale set is $tags")
        // pt is carried because pt-BR inherits from it, not because it was asked
        // for; what matters is that the 1100-odd others are gone.
        assertTrue(tags.size < 10, "expected a handful of locales, got ${tags.size}")
    }

    @Test
    fun `the codes and lookups are unaffected by narrowing the locales`() {
        // Country and Currency come from the shipped -types artifacts here,
        // because this build narrowed its locales and not its entry sets. An
        // arbitrary code from a payment API still resolves. Adding
        // country { entries(...) } would be the trade that changes this.
        assertEquals(Country.BR, Country.forAlpha2("br"))
        assertEquals("BR", Country.BR.alpha2)
        assertEquals("BRA", Country.BR.alpha3)
        assertEquals(Currency.JPY, Currency.forCode("jpy"))
    }

    @Test
    fun `the catalog names the locales this build generated`() {
        // Generated into com.example.locale.catalog rather than taken from
        // kotlinx-locale-types, which is not on the classpath. JA exists because
        // this build declared ja; a language it did not declare would not
        // compile here at all.
        assertEquals("pt-BR", PT.BR.tag)
        assertEquals(ptBr, PT.BR.toLocale())
        assertEquals(ja, JA.toLocale())
        // PT itself is the bare language, and pt is here because pt-BR inherits
        // from it. Its own entries are the pt-* locales this build kept.
        assertEquals("pt", PT.tag)
    }

    @Test
    fun `formats money and dates in the generated locales`() {
        val price = CurrencyAmount(Currency.BRL, 123456)
        // CLDR separates the symbol from the number with U+00A0 in pt-BR, not a
        // plain space. Spelling it out beats normalizing it away, since a change
        // here would be a real change in what users see.
        assertEquals("R$ 1.234,56", price.format(ptBr))
        assertEquals("R$", Currency.BRL.symbol(ptBr))

        val date = LocalDate(2026, 7, 27)
        assertEquals("27 de julho de 2026", date.format(FormatStyle.LONG, ptBr))
        assertEquals("July 27, 2026", date.format(FormatStyle.LONG, en))
    }

    @Test
    fun `names a currency in words in the generated locales`() {
        // The plural table is the one whose record carries three separate keyed
        // fields: the count-keyed names, the pattern joining a name to a number,
        // and the number formatting itself. Narrowing rebuilds all three, and a
        // build that lost any of them would still compile and still answer, just
        // wrongly, so the answers are what this checks.
        assertEquals("2.00 US dollars", CurrencyAmount(Currency.USD, 200).formatPluralName(en))
        assertEquals("1 US dollar", CurrencyAmount(Currency.USD, 100).formatPluralName(en, fractionDigits = 0))
        assertEquals("2,00 Dólares americanos", CurrencyAmount(Currency.USD, 200).formatPluralName(ptBr))
        // Japanese joins the number and the name with nothing at all, which is
        // its own unitPattern rather than root's, so this fails if the pattern
        // field did not survive narrowing.
        assertEquals("2.00米ドル", CurrencyAmount(Currency.USD, 200).formatPluralName(ja))

        assertEquals("US dollar", Currency.USD.pluralName(1, en))
        assertEquals("US dollars", Currency.USD.pluralName(2, en))
    }

    @Test
    fun `falls back to the configured locale for a currency name in words`() {
        val german = Locale.forLanguageTag("de")
        assertEquals("2.00 US dollars", CurrencyAmount(Currency.USD, 200).formatPluralName(german))
    }
}
