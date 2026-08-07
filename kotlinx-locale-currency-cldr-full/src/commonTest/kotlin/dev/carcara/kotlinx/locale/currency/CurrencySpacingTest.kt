package dev.carcara.kotlinx.locale.currency

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.cldr.format
import dev.carcara.kotlinx.locale.currency.cldr.parseFormattedOrNull
import dev.carcara.kotlinx.locale.test.assertEquals

/**
 * UTS #35 currency spacing: the space CLDR puts between a currency and the
 * digits when the character between them is neither a symbol nor a separator.
 *
 * Every expectation here was read off ICU 78.3, the release the pinned CLDR was
 * built against, by formatting the same amount through
 * `NumberFormat.getCurrencyInstance`. That mattered: the JDK's `java.text`
 * formatter does not implement the rule at all and writes `AED1 234,56` for the
 * first case, so it agrees with a build that has this switched off and is no use
 * as a second opinion on it.
 */
val CurrencySpacingTest by matrixSuite {

    val amount = 123456L
    fun locale(tag: String) = Locale.forLanguageTag(tag)
    fun format(code: String, tag: String) = CurrencyAmount(Currency.forCode(code), amount).format(locale(tag))

    test("insertsTheSpaceWhereTheCurrencyEndsInALetterOrPoint") {
        // A currency with no symbol in this locale falls back to its ISO code,
        // and a letter against a digit earns the space.
        assertEquals("AED\u00A01\u00A0234,56", format("AED", "en-ZA"))
        assertEquals("R\u00A01\u00A0234,56", format("ZAR", "en-ZA"))
        // Cg. ends in a full stop, which is punctuation rather than a symbol.
        assertEquals("Cg.\u00A01\u00A0234,56", format("XCG", "af"))
        assertEquals("Cg.\u00A01,234.56", format("XCG", "en"))
        assertEquals("රු.\u00A01,234.56", format("LKR", "si"))
    }

    test("leavesSymbolCurrenciesAlone") {
        // `$` is a currency symbol, which root's currencyMatch excludes.
        assertEquals("\$1,234.56", format("USD", "en"))
        // `US$` ends in one too, so the space here is the pattern's own.
        assertEquals("US\$\u00A01.234,56", format("USD", "pt-BR"))
        assertEquals("US\$1\u00A0234,56", format("USD", "en-ZA"))
    }

    test("doesNotDoubleTheSpaceAPatternAlreadyWrites") {
        // da writes `#,##0.00 ¤` with the space in the pattern, so the rule has
        // no boundary to act on and `kr.` keeps exactly one space.
        assertEquals("1.234,56\u00A0kr.", format("DKK", "da"))
    }

    test("theInsertedSpaceReadsBack") {
        // Parsing tolerates it without requiring it, which is what ICU means by
        // letting currency spacing be a weak match.
        val en = locale("en")
        assertEquals(amount, CurrencyAmount.parseFormattedOrNull(Currency.forCode("XCG"), "Cg.\u00A01,234.56", en)?.minorUnits)
        assertEquals(amount, CurrencyAmount.parseFormattedOrNull(Currency.forCode("XCG"), "Cg.1,234.56", en)?.minorUnits)
    }
}
