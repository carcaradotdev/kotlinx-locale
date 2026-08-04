package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.icuCurrencyFormatGoldenData
import dev.carcara.kotlinx.locale.conformance.icuCurrencyFormatGoldenMinorUnits
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.cldr.format
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Holds formatted currency output to what ICU writes for the same amount.
 *
 * The other currency goldens compare tables. This one compares what comes out
 * once the formatter has run over them, which is where pattern selection, the
 * `alphaNextToNumber` variant, currency spacing, symbol substitution and the
 * sign all meet. Currency spacing changed 500 outputs and every table golden
 * passed unchanged either side of it, so this is the file that would have
 * noticed.
 *
 * A pair whose symbol ICU spells differently from CLDR `release-48-2` is
 * skipped rather than failed. ICU 78.3 is built from a nearby but different CLDR
 * snapshot, and a symbol that moved between the two would report a data
 * difference as a formatting bug. The skip is narrow, one currency and width in
 * one locale, and [everyGoldenLocaleIsMostlyComparable] fails if it ever stops
 * being narrow.
 */
class IcuCurrencyFormatGoldenTest {

    private fun style(name: String): CurrencySymbolStyle = CurrencySymbolStyle.valueOf(name)

    /** ICU and CLDR point releases disagree about which no-break space they use. */
    private fun String.normalizedSpaces(): String = replace(' ', ' ').replace(' ', ' ')

    @Test
    fun formattedOutputMatchesIcu() {
        var compared = 0
        val mismatches = ArrayList<String>()
        for ((tag, golden) in icuCurrencyFormatGoldenData) {
            val locale = Locale.forLanguageTag(tag)
            for ((key, expected) in golden.answers) {
                val code = key.substringBefore('|')
                val symbolStyle = style(key.substringAfter('|'))
                val currency = Currency.forCodeOrNull(code) ?: continue

                // Only compare where ICU formatted from the spelling we carry.
                val icuSymbol = golden.symbols[key] ?: continue
                val ours = CldrCurrency.symbol(currency, locale, symbolStyle)
                if (ours.normalizedSpaces() != icuSymbol.normalizedSpaces()) continue

                for ((index, minorUnits) in icuCurrencyFormatGoldenMinorUnits.withIndex()) {
                    val actual = CurrencyAmount(currency, minorUnits).format(locale, symbolStyle)
                    if (expected[index].normalizedSpaces() != actual.normalizedSpaces()) {
                        mismatches.add("$tag $code $symbolStyle $minorUnits: icu='${expected[index]}' ours='$actual'")
                    }
                    compared++
                }
            }
        }
        assertTrue(compared > 15000, "expected the goldens to compare thousands of strings, compared $compared")
        // Every difference rather than the first, because these tend to arrive
        // as one cause with hundreds of faces: the sign this fixture first
        // caught was a single bug wearing 249 of them.
        assertTrue(
            mismatches.isEmpty(),
            "${mismatches.size} of $compared outputs differ from ICU:\n" + mismatches.take(10).joinToString("\n"),
        )
    }

    @Test
    fun everyGoldenLocaleIsMostlyComparable() {
        // The skip above is meant to catch a handful of currencies whose symbol
        // moved between the two releases. A locale where it fires for most of
        // the set means something else is wrong, and comparing what is left
        // would be a test that quietly stopped testing.
        for ((tag, golden) in icuCurrencyFormatGoldenData) {
            val locale = Locale.forLanguageTag(tag)
            val total = golden.symbols.size
            val agreeing = golden.symbols.count { (key, icuSymbol) ->
                val currency = Currency.forCodeOrNull(key.substringBefore('|'))
                currency != null &&
                    CldrCurrency.symbol(currency, locale, style(key.substringAfter('|'))).normalizedSpaces() ==
                    icuSymbol.normalizedSpaces()
            }
            assertTrue(agreeing * 2 > total, "$tag: only $agreeing of $total spellings match ICU")
        }
    }
}
