package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.cldr.format
import dev.carcara.kotlinx.locale.number.NumberNotation
import dev.carcara.kotlinx.locale.number.SignDisplay
import kotlin.test.Test
import kotlin.test.assertEquals

private val EN = Locale.of("en")
private val EN_GB = Locale.of("en", region = "GB")

/** The options CLDR carries but the first shape of this API had no way to ask for. */
class CurrencyFormatOptionsTest {

    @Test
    fun fractionDigitsOverrideTheCurrencysOwn() {
        val amount = CurrencyAmount(Currency.GBP, 1850000)
        assertEquals("£18,500.00", amount.format(EN_GB))
        // A headline figure wants the digits gone, and rounding the amount first
        // does not help: the pattern would still print CLDR's two.
        assertEquals("£18,500", amount.format(EN_GB, fractionDigits = 0))
        assertEquals("£18,500.000", amount.format(EN_GB, fractionDigits = 3))
    }

    @Test
    fun fractionDigitsApplyAfterTheCurrencysRoundingIncrement() {
        // Swiss francs round to 0.05 in cash. The override rescales what the
        // increment produced rather than replacing it.
        val amount = CurrencyAmount(Currency.CHF, 1003)
        assertEquals("CHF\u00A010.05", amount.format(EN, cash = true))
        assertEquals("CHF\u00A010", amount.format(EN, cash = true, fractionDigits = 0))
    }

    @Test
    fun signDisplayCoversWhatAccountingUsedTo() {
        val negative = CurrencyAmount(Currency.USD, -123456)
        val positive = CurrencyAmount(Currency.USD, 123456)
        assertEquals("-$1,234.56", negative.format(EN))
        assertEquals("($1,234.56)", negative.format(EN, signDisplay = SignDisplay.ACCOUNTING))
        assertEquals("$1,234.56", positive.format(EN))
        // The value a transaction list wants: a sign on every movement.
        assertEquals("+$1,234.56", positive.format(EN, signDisplay = SignDisplay.EXCEPT_ZERO))
        assertEquals("$0.00", CurrencyAmount(Currency.USD, 0).format(EN, signDisplay = SignDisplay.EXCEPT_ZERO))
        assertEquals("$1,234.56", negative.format(EN, signDisplay = SignDisplay.NEVER))
    }

    @Test
    fun compactMoneyUsesTheCurrencyCompactTable() {
        // en-GB writes compact money in lower case, which is its own CLDR data
        // rather than a variation on en.
        assertEquals("£1.2m", CurrencyAmount(Currency.GBP, 120000000).format(EN_GB, notation = NumberNotation.COMPACT_SHORT))
        assertEquals("$12K", CurrencyAmount(Currency.USD, 1234500).format(EN, notation = NumberNotation.COMPACT_SHORT))
    }
}
