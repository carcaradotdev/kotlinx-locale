package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToCurrencyFormats
import dev.carcara.kotlinx.locale.conformance.assertConformsToCurrencyNames
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.platform.PlatformCurrency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the platform currency source does, and where it deliberately does not.
 *
 * The composition is what an application ships, so the conformance suite runs
 * against that. `PlatformCurrency` alone is allowed gaps, and it has more of them
 * than the country source: cash rounding is not a platform concept anywhere,
 * accounting is missing on JVM, and parsing exists only where it is exact.
 */
class PlatformCurrencyTest {

    private val names = FallbackCurrencyNames(primary = PlatformCurrency, fallback = CldrCurrency)
    private val formats = FallbackCurrencyFormats(primary = PlatformCurrency, fallback = CldrCurrency)

    private val en = Locale.of("en")

    @Test
    fun theCompositionConformsOnNames() {
        names.assertConformsToCurrencyNames(ConformanceTier.BEHAVIOURAL)
    }

    @Test
    fun theCompositionRendersEveryAmount() {
        // Deliberately not the conformance round trip. That asks one source to
        // read back what it wrote, and a composition can format with the platform
        // and parse with the bundled source. The two do not agree on every glyph:
        // Foundation writes ¥ for JPY in ja where CLDR writes ￥, so the CLDR
        // parser does not recognize its own currency in Foundation's output.
        // Cross-source round tripping is not something the library promises, and
        // pretending otherwise here would hide that.
        //
        // What composition does promise is that something always answers.
        for (tag in listOf("en", "de", "ja", "pt-BR")) {
            val locale = Locale.forLanguageTag(tag)
            for (code in listOf("USD", "EUR", "JPY", "BHD", "CHF", "HUF")) {
                val currency = Currency.forCode(code)
                for (minorUnits in listOf(0L, 1, -1, 123456, -123456)) {
                    val amount = CurrencyAmount(currency, minorUnits)
                    assertTrue(formats.format(amount, locale).isNotBlank(), "$tag $code $minorUnits rendered nothing")
                    assertTrue(
                        formats.format(amount, locale, CurrencySymbolStyle.CODE, accounting = true).isNotBlank(),
                        "$tag $code $minorUnits rendered nothing for the accounting style",
                    )
                }
            }
        }
    }

    @Test
    fun thePlatformSourceIsSelfConsistentWhereItParses() {
        // JVM and Android only: Intl has no parser and Foundation's is lossy, so
        // elsewhere there is no self-consistency to check.
        if (PlatformCurrency.parseToMinorUnitsOrNull("1.00", "USD", en) == null) return
        PlatformCurrency.assertConformsToCurrencyFormats(ConformanceTier.BEHAVIOURAL)
    }

    @Test
    fun cashRoundingAlwaysMissesBecauseNoPlatformHasIt() {
        // CLDR knows CHF cash rounds to 0.05. No platform formatter does, so this
        // is a miss on every target, which is what sends it to the fallback.
        val chf = Currency.forCode("CHF")
        assertEquals(
            null,
            PlatformCurrency.formatOrNull(1234, chf.code, en, CurrencySymbolStyle.SYMBOL, accounting = false, cash = true),
        )
        // The composition still answers, which is the point of composing.
        assertTrue(formats.format(CurrencyAmount(chf, 1234), en, cash = true).isNotBlank())
    }

    @Test
    fun anUnknownCodeMissesRatherThanGuessing() {
        assertEquals(null, PlatformCurrency.formatOrNull(100, "ZZZ", en, CurrencySymbolStyle.SYMBOL, false, false))
        assertEquals(null, PlatformCurrency.parseToMinorUnitsOrNull("1.00", "ZZZ", en))
    }

    @Test
    fun theUnavailableTargetsSaySoRatherThanAnsweringBadly() {
        if (PlatformCurrency.isAvailable) return
        assertEquals(null, PlatformCurrency.currencySymbolOrNull("USD", en))
        assertEquals(null, PlatformCurrency.formatOrNull(123456, "USD", en, CurrencySymbolStyle.SYMBOL, false, false))
        assertTrue(PlatformCurrency.supportedLocales.isEmpty())
    }

    @Test
    fun theAvailableTargetsFormatAnExactAmount() {
        if (!PlatformCurrency.isAvailable) return
        val formatted = assertNotNull(
            PlatformCurrency.formatOrNull(123456, "USD", en, CurrencySymbolStyle.SYMBOL, false, false),
            "the platform did not format USD in English",
        )
        // Not asserting the exact string: the host decides the grouping and the
        // symbol placement, and that is the whole reason to use it. What must hold
        // is that the value survived, digits and all.
        assertTrue("1" in formatted && "234" in formatted && "56" in formatted, "lost the amount: '$formatted'")
        assertTrue(formatted.any { it.isDigit() })
    }

    @Test
    fun theAvailableTargetsKeepLargeAmountsExact() {
        if (!PlatformCurrency.isAvailable) return
        // Past 2^53 minor units, which is where a Double would start rounding. The
        // amount crosses to the platform as a decimal string precisely so this
        // holds.
        val formatted = assertNotNull(
            PlatformCurrency.formatOrNull(9007199254740993, "USD", en, CurrencySymbolStyle.SYMBOL, false, false),
        )
        val digits = formatted.filter { it.isDigit() }
        assertTrue(digits.endsWith("93"), "the last minor units were rounded away: '$formatted'")
    }

    @Test
    fun theIsoCodeStyleWritesTheCodeRatherThanTheSymbol() {
        if (!PlatformCurrency.isAvailable) return
        val withCode = assertNotNull(
            PlatformCurrency.formatOrNull(123456, "USD", en, CurrencySymbolStyle.CODE, false, false),
        )
        assertTrue("USD" in withCode, "the ISO code style did not write the code: '$withCode'")
    }

    @Test
    fun theAvailableTargetsNameAndSymbolizeTheMajorCurrencies() {
        if (!PlatformCurrency.isAvailable) return
        for (code in listOf("USD", "EUR", "JPY", "BRL")) {
            assertNotNull(PlatformCurrency.currencyNameOrNull(code, en), "no English name for $code")
            assertNotNull(PlatformCurrency.currencySymbolOrNull(code, en), "no English symbol for $code")
        }
    }
}
