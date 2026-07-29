import com.example.locale.GeneratedCountryNames
import com.example.locale.displayName
import com.example.locale.format
import com.example.locale.symbol
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.country.forAlpha2
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.forCode
import dev.carcara.kotlinx.locale.datetime.FormatStyle
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
    fun `the codes and lookups are unaffected by narrowing`() {
        // These come from -core and -types, which the plugin does not touch, so
        // an arbitrary code from a payment API still resolves.
        assertEquals(Country.BR, Country.forAlpha2("br"))
        assertEquals("BR", Country.BR.alpha2)
        assertEquals("BRA", Country.BR.alpha3)
        assertEquals(Currency.JPY, Currency.forCode("jpy"))
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
}
