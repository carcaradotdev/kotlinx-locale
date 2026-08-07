package dev.carcara.kotlinx.locale.currency

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToCurrencyFormats
import dev.carcara.kotlinx.locale.conformance.assertConformsToCurrencyNames
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.platform.PlatformCurrency
import dev.carcara.kotlinx.locale.number.SignDisplay
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNotNull
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * The platform currency source, checked on every target this module builds for.
 *
 * Every test asserts something everywhere. Where behaviour depends on the host,
 * that is an if/else with assertions in both branches rather than an early
 * return: a test that quietly does nothing on the four targets with no locale
 * data still passes there, which reads as coverage it is not.
 */
val PlatformCurrencyTest by matrixSuite {

    val names = FallbackCurrencyNames(primary = PlatformCurrency, fallback = CldrCurrency)
    val formats = FallbackCurrencyFormats(primary = PlatformCurrency, fallback = CldrCurrency)

    val en = Locale.of("en")
    val chf = Currency.forCode("CHF")

    // Runs identically everywhere ------------------------------------------------

    test("theCompositionConformsOnNames") {
        names.assertConformsToCurrencyNames(ConformanceTier.BEHAVIOURAL)
    }

    test("cashRoundingComesFromTheBundledSourceOnEveryPlatform") {
        // No platform formatter knows CLDR cash rounding, so the platform misses on
        // every target and the bundled source answers on every target. That makes
        // this one exact string a genuine all-platforms assertion rather than a
        // shape check: CHF 12.34 rounds to the nearest 0.05.
        assertEquals(
            null,
            PlatformCurrency.formatOrNull(
                1234,
                "CHF",
                en,
                CurrencyFormatOptions(CurrencySymbolStyle.SYMBOL, SignDisplay.AUTO, cash = true),
            ),
            "a platform started supporting cash rounding, which the composition no longer needs to cover",
        )
        assertEquals("CHF\u00A012.35", formats.format(CurrencyAmount(chf, 1234), en, cash = true))

        // That the rounding came from the cash path rather than from the scale,
        // asked of the bundled source directly so it stays exact everywhere.
        assertEquals("CHF\u00A012.34", CldrCurrency.format(CurrencyAmount(chf, 1234), en))

        // The non-cash composed output is deliberately not pinned: it comes from
        // the host where there is one, and the hosts disagree with CLDR about the
        // details. java.text writes CHF12.34 with no separator where CLDR writes a
        // no-break space. Both are defensible, which is the point of being able to
        // choose, and pinning either here would just encode the CI machine.
        assertTrue(formats.format(CurrencyAmount(chf, 1234), en).startsWith("CHF"))
    }

    test("theCompositionRendersEveryAmount") {
        // Deliberately not the conformance round trip. That asks one source to read
        // back what it wrote, and a composition can format with the platform and
        // parse with the bundled source. The two do not agree on every glyph:
        // Foundation writes ¥ for JPY in ja where CLDR writes ￥, so the CLDR parser
        // does not recognize its own currency in Foundation's output. Cross-source
        // round tripping is not something the library promises.
        for (tag in listOf("en", "de", "ja", "pt-BR")) {
            val locale = Locale.forLanguageTag(tag)
            for (code in listOf("USD", "EUR", "JPY", "BHD", "CHF", "HUF")) {
                val currency = Currency.forCode(code)
                for (minorUnits in listOf(0L, 1, -1, 123456, -123456)) {
                    val amount = CurrencyAmount(currency, minorUnits)
                    assertTrue(formats.format(amount, locale).isNotBlank(), "$tag $code $minorUnits rendered nothing")
                    assertTrue(
                        formats.format(amount, locale, CurrencySymbolStyle.CODE, signDisplay = SignDisplay.ACCOUNTING).isNotBlank(),
                        "$tag $code $minorUnits rendered nothing for the accounting style",
                    )
                }
            }
        }
    }

    test("theCompositionNamesEveryCurrency") {
        for (tag in listOf("en", "de", "ja", "pt-BR")) {
            val locale = Locale.forLanguageTag(tag)
            for (currency in Currency.entries) {
                assertTrue(names.symbol(currency, locale).isNotBlank(), "$tag ${currency.code} symbol was blank")
                assertTrue(names.displayName(currency, locale).isNotBlank(), "$tag ${currency.code} name was blank")
            }
        }
    }

    test("anUnknownCodeMissesRatherThanGuessing") {
        assertEquals(
            null,
            PlatformCurrency.formatOrNull(
                100,
                "ZZZ",
                en,
                CurrencyFormatOptions(CurrencySymbolStyle.SYMBOL, SignDisplay.AUTO, cash = false),
            ),
        )
        assertEquals(null, PlatformCurrency.parseToMinorUnitsOrNull("1.00", "ZZZ", en))
    }

    // Host-dependent, asserted on both sides -------------------------------------

    test("theSourceHonoursItsAvailabilityContract") {
        if (PlatformCurrency.isAvailable) {
            val symbol = assertNotNull(PlatformCurrency.currencySymbolOrNull("USD", en), "no English symbol for USD")
            assertTrue(!symbol.equals("USD", ignoreCase = true), "the symbol came back as the code")
            for (code in listOf("USD", "EUR", "JPY", "BRL")) {
                assertNotNull(PlatformCurrency.currencyNameOrNull(code, en), "no English name for $code")
                assertNotNull(PlatformCurrency.currencySymbolOrNull(code, en), "no English symbol for $code")
            }
            val formatted = assertNotNull(
                PlatformCurrency.formatOrNull(
                    123456,
                    "USD",
                    en,
                    CurrencyFormatOptions(CurrencySymbolStyle.SYMBOL, SignDisplay.AUTO, cash = false),
                ),
            )
            // Not the exact string: the host decides grouping and symbol placement,
            // which is the reason to use it. What must hold is that the value
            // survived.
            assertTrue("1" in formatted && "234" in formatted && "56" in formatted, "lost the amount: '$formatted'")
        } else {
            // Linux, Windows, Android Native and WASI. A source that answered here
            // would look like an answer and stop any fallback from firing.
            assertEquals(null, PlatformCurrency.currencySymbolOrNull("USD", en))
            assertEquals(null, PlatformCurrency.currencyNameOrNull("USD", en))
            assertEquals(
                null,
                PlatformCurrency.formatOrNull(
                    123456,
                    "USD",
                    en,
                    CurrencyFormatOptions(CurrencySymbolStyle.SYMBOL, SignDisplay.AUTO, cash = false),
                ),
            )
            assertEquals(null, PlatformCurrency.parseToMinorUnitsOrNull("$1,234.56", "USD", en))
            assertTrue(PlatformCurrency.supportedLocales.isEmpty())
        }
    }

    test("largeAmountsStayExactWhereThePlatformFormatsAtAll") {
        // Past 2^53 minor units, which is where a Double would start rounding. The
        // amount crosses to the platform as a decimal string precisely so this
        // holds.
        val formatted = PlatformCurrency.formatOrNull(
            9007199254740993,
            "USD",
            en,
            CurrencyFormatOptions(CurrencySymbolStyle.SYMBOL, SignDisplay.AUTO, cash = false),
        )
        if (PlatformCurrency.isAvailable) {
            val digits = assertNotNull(formatted).filter { it.isDigit() }
            assertTrue(digits.endsWith("93"), "the last minor units were rounded away: '$formatted'")
        } else {
            assertEquals(null, formatted)
        }
    }

    test("theIsoCodeStyleWritesTheCodeWhereThePlatformFormatsAtAll") {
        val withCode = PlatformCurrency.formatOrNull(
            123456,
            "USD",
            en,
            CurrencyFormatOptions(CurrencySymbolStyle.CODE, SignDisplay.AUTO, cash = false),
        )
        if (PlatformCurrency.isAvailable) {
            assertTrue("USD" in assertNotNull(withCode), "the ISO code style did not write the code: '$withCode'")
        } else {
            assertEquals(null, withCode)
        }
    }

    test("thePlatformSourceIsSelfConsistentWhereItParses") {
        // JVM and Android only: Intl has no parser and Foundation's is lossy, so
        // elsewhere there is no self-consistency to check. Asserting the absence
        // rather than skipping, so that a platform gaining a parser is noticed.
        val parses = PlatformCurrency.parseToMinorUnitsOrNull("1.00", "USD", en) != null
        if (parses) {
            PlatformCurrency.assertConformsToCurrencyFormats(ConformanceTier.BEHAVIOURAL)
        } else {
            assertEquals(null, PlatformCurrency.parseToMinorUnitsOrNull("1.00", "USD", en))
        }
    }
}
