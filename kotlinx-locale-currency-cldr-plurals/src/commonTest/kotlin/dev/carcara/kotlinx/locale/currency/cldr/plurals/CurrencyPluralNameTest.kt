package dev.carcara.kotlinx.locale.currency.cldr.plurals

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.number.NumberGrouping
import dev.carcara.kotlinx.locale.number.SignDisplay
import dev.carcara.kotlinx.locale.test.assertEquals

/**
 * The name form by hand, where the golden fixture varies data and leaves the
 * options at their defaults.
 *
 * [IcuCurrencyPluralGoldenTest] compares twenty three thousand strings across
 * sixty one locales, but only at two digit counts and with every other option
 * where it started. These are the cases those axes reach: the sign, cash
 * rounding, grouping, and the boundary where a rounded amount keeps its minus.
 */
val CurrencyPluralNameTest by matrixSuite {

    val en = Locale.forLanguageTag("en")
    val cs = Locale.forLanguageTag("cs")
    val pl = Locale.forLanguageTag("pl")
    val hu = Locale.forLanguageTag("hu")

    fun usd(minorUnits: Long) = CurrencyAmount(Currency.USD, minorUnits)

    test("writesTheCurrencyInWords") {
        assertEquals("2.00 US dollars", usd(2_00).formatPluralName(en))
        assertEquals("1 US dollar", usd(1_00).formatPluralName(en, fractionDigits = 0))
        assertEquals("2 US dollars", usd(2_00).formatPluralName(en, fractionDigits = 0))
    }

    test("agreesWithTheNumberAsItIsPrinted") {
        // Czech has one for a bare 1 and many for anything with a fraction
        // digit, so the same amount takes two spellings at two digit counts.
        assertEquals("1 americký dolar", usd(1_00).formatPluralName(cs, fractionDigits = 0))
        assertEquals("1,00 amerického dolaru", usd(1_00).formatPluralName(cs))
        // Polish separates one, few and many, and all three are reachable once
        // the fraction digits are gone.
        assertEquals("1 dolar amerykański", usd(1_00).formatPluralName(pl, fractionDigits = 0))
        assertEquals("2 dolary amerykańskie", usd(2_00).formatPluralName(pl, fractionDigits = 0))
        assertEquals("5 dolarów amerykańskich", usd(5_00).formatPluralName(pl, fractionDigits = 0))
    }

    test("readsTheCurrencyFractionDigits") {
        // The forint prints none and the dinar prints three, both from CLDR
        // rather than from ISO, which gives the forint two.
        assertEquals("1 Hungarian forint", CurrencyAmount(Currency.HUF, 1_00).formatPluralName(en))
        assertEquals("1.000 Bahraini dinars", CurrencyAmount(Currency.BHD, 1_000).formatPluralName(en))
    }

    test("roundsCashTheWayTheCurrencyDoes") {
        // The Swiss franc rounds to the nearest 0.05 in cash and to the cent
        // otherwise.
        val chf = CurrencyAmount(Currency.CHF, 1_02)
        assertEquals("1.02 Swiss francs", chf.formatPluralName(en))
        assertEquals("1.00 Swiss francs", chf.formatPluralName(en, cash = true))
    }

    test("keepsTheSignOnAnAmountThatRoundsAway") {
        // The same boundary the symbol form has: a negative amount that rounds
        // to zero still reads as negative.
        assertEquals("-0 Hungarian forints", CurrencyAmount(Currency.HUF, -1).formatPluralName(en))
        assertEquals("-0.00 Swiss francs", CurrencyAmount(Currency.CHF, -2).formatPluralName(en, cash = true))
    }

    test("writesTheSignTheCallerAsksFor") {
        assertEquals("+2.00 US dollars", usd(2_00).formatPluralName(en, signDisplay = SignDisplay.ALWAYS))
        assertEquals("2.00 US dollars", usd(-2_00).formatPluralName(en, signDisplay = SignDisplay.NEVER))
        // The accounting values pick CLDR's accounting pattern in the symbol
        // form, and there is no currency pattern here for them to pick, so they
        // write the same minus AUTO does rather than parentheses.
        assertEquals("-2.00 US dollars", usd(-2_00).formatPluralName(en, signDisplay = SignDisplay.ACCOUNTING))
    }

    test("groupsTheNumberTheWayTheLocaleGroupsAnyNumber") {
        assertEquals("1,234.56 US dollars", usd(1234_56).formatPluralName(en))
        assertEquals("1234.56 US dollars", usd(1234_56).formatPluralName(en, grouping = NumberGrouping.NEVER))
        // Hungarian groups only from five digits, which is its own
        // minimumGroupingDigits rather than anything about money.
        assertEquals("1234 magyar forint", CurrencyAmount(Currency.HUF, 1234_00).formatPluralName(hu))
        assertEquals("12 345 magyar forint", CurrencyAmount(Currency.HUF, 12345_00).formatPluralName(hu))
    }

    test("namesACurrencyForACount") {
        assertEquals("US dollar", Currency.USD.pluralName(1, en))
        assertEquals("US dollars", Currency.USD.pluralName(2, en))
        assertEquals("dolar amerykański", Currency.USD.pluralName(1, pl))
        assertEquals("dolary amerykańskie", Currency.USD.pluralName(2, pl))
        assertEquals("dolarów amerykańskich", Currency.USD.pluralName(5, pl))
    }

    test("fallsBackThroughTheChainUts35Prescribes") {
        // The Belgian franc has a Polish display name and no count-keyed
        // spelling, so every category reads the one name it has.
        assertEquals("frank belgijski", Currency.BEF.pluralName(1, pl))
        assertEquals("frank belgijski", Currency.BEF.pluralName(5, pl))
        // And a locale with no currency names at all answers the ISO code.
        val unknown = Locale.forLanguageTag("kea")
        assertEquals("XAG", Currency.XAG.pluralName(2, unknown))
    }
}
