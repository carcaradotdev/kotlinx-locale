package dev.carcara.kotlinx.locale.number

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.number.cldr.CldrNumber
import dev.carcara.kotlinx.locale.number.cldr.format
import dev.carcara.kotlinx.locale.number.cldr.formatCompact
import dev.carcara.kotlinx.locale.number.cldr.formatOrdinal
import dev.carcara.kotlinx.locale.number.cldr.formatPercent
import dev.carcara.kotlinx.locale.number.cldr.numberSymbols
import dev.carcara.kotlinx.locale.number.cldr.parseNumberOrNull
import dev.carcara.kotlinx.locale.number.cldr.pluralCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val EN = Locale.of("en")
private val DE = Locale.of("de")
private val CS = Locale.of("cs")
private val TR = Locale.of("tr")
private val PL = Locale.of("pl")

class CldrNumberTest {

    @Test
    fun groupsWithTheLocaleSeparators() {
        assertEquals("1,234,567", 1234567L.format(EN))
        assertEquals("1.234.567", 1234567L.format(DE))
        // Czech groups with a no-break space, which is the character CLDR
        // declares and not the ASCII one.
        assertEquals("1 234 567", 1234567L.format(CS))
    }

    @Test
    fun honoursMinimumGroupingDigits() {
        // Polish declares minimumGroupingDigits 2, so a four-digit number does
        // not group and a five-digit one does.
        assertEquals("1000", 1000L.format(PL))
        assertEquals("10 000", 10000L.format(PL))
        assertEquals("1,000", 1000L.format(EN))
    }

    @Test
    fun percentTakesAFractionAndCarriesTheLocalePlacement() {
        val eighth = Decimal.parse("0.125")
        // 12.5 rounds to 12 rather than 13: LDML says nothing about the rounding
        // mode, so this library follows ICU and rounds half to even, which is
        // also what java.text.DecimalFormat does. ECMA-402 rounds half away from
        // zero and would print 13%.
        assertEquals("12%", eighth.formatPercent(EN))
        assertEquals("12.5%", eighth.formatPercent(EN, fractionDigits = 1))
        // Czech and German put a no-break space before the sign.
        assertEquals("12,5 %", eighth.formatPercent(CS, fractionDigits = 1))
        assertEquals("12,5 %", eighth.formatPercent(DE, fractionDigits = 1))
        // Turkish puts the sign in front.
        assertEquals("%12,5", eighth.formatPercent(TR, fractionDigits = 1))
    }

    @Test
    fun compactPinsItsDefaultPrecision() {
        // Two significant digits or none, whichever keeps more: the value this
        // library pins where UTS #35 says only "typically".
        assertEquals("1.2K", 1200L.formatCompact(EN))
        assertEquals("12K", 12345L.formatCompact(EN))
        assertEquals("123K", 123456L.formatCompact(EN))
        // Rounding crosses a bucket rather than printing 1000K.
        assertEquals("1M", 999999L.formatCompact(EN))
        assertEquals("1.2M", 1200000L.formatCompact(EN))
    }

    @Test
    fun compactLongUsesTheLocaleWording() {
        assertEquals("1.2 thousand", 1200L.formatCompact(EN, NumberNotation.COMPACT_LONG))
        assertTrue(1200L.formatCompact(DE, NumberNotation.COMPACT_LONG).isNotEmpty())
    }

    @Test
    fun pluralCategoryReadsTheVisibleDigits() {
        // The Czech rule is `one: i = 1 and v = 0` and `many: v != 0`, so the
        // same numeric value falls into different categories depending on how it
        // is about to be printed.
        assertEquals(PluralCategory.ONE, 1L.pluralCategory(CS))
        assertEquals(PluralCategory.MANY, Decimal.parse("1.0").pluralCategory(1, CS))
        assertEquals(PluralCategory.FEW, 3L.pluralCategory(CS))
        assertEquals(PluralCategory.OTHER, 10L.pluralCategory(CS))
        assertEquals(PluralCategory.ONE, 1L.pluralCategory(EN))
        assertEquals(PluralCategory.OTHER, 2L.pluralCategory(EN))
    }

    @Test
    fun ordinalsFollowTheRuleSetOrRootsFullStop() {
        assertEquals("1st", 1L.formatOrdinal(EN))
        assertEquals("2nd", 2L.formatOrdinal(EN))
        assertEquals("3rd", 3L.formatOrdinal(EN))
        assertEquals("4th", 4L.formatOrdinal(EN))
        assertEquals("11th", 11L.formatOrdinal(EN))
        assertEquals("21st", 21L.formatOrdinal(EN))
        // German, Czech and the rest inherit root's rule, which appends a full
        // stop. That is the correct form rather than a gap in the data.
        assertEquals("1.", 1L.formatOrdinal(DE))
        assertEquals("2.", 2L.formatOrdinal(CS))
    }

    @Test
    fun signDisplayPutsThePlusWhereTheLocaleDoes() {
        assertEquals("+42", 42L.format(EN, signDisplay = SignDisplay.ALWAYS))
        assertEquals("-42", (-42L).format(EN))
        assertEquals("42", 42L.format(EN))
        // EXCEPT_ZERO is the one a transaction list wants: a sign on every
        // movement, nothing on a zero balance.
        assertEquals("+42", 42L.format(EN, signDisplay = SignDisplay.EXCEPT_ZERO))
        assertEquals("0", 0L.format(EN, signDisplay = SignDisplay.EXCEPT_ZERO))
        assertEquals("+0", 0L.format(EN, signDisplay = SignDisplay.ALWAYS))
        assertEquals("42", (42L).format(EN, signDisplay = SignDisplay.NEVER))
        assertEquals("42", (-42L).format(EN, signDisplay = SignDisplay.NEVER))
    }

    @Test
    fun symbolsAreHandedOutWhole() {
        val czech = numberSymbols(CS)
        assertEquals(",", czech.decimal)
        assertEquals(" ", czech.group)
        assertEquals("%", czech.percentSign)
        assertEquals(listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), czech.digits)
        assertEquals(1, czech.minimumGroupingDigits)
        assertEquals(2, numberSymbols(PL).minimumGroupingDigits)
    }

    @Test
    fun parsingKeepsTheDigitsItWasGiven() {
        val parsed = parseNumberOrNull("1.50", EN)
        assertEquals(Decimal.parse("1.50"), parsed)
        assertEquals(2, parsed?.scale, "the scale is what the plural rules read")
        assertEquals(Decimal.parse("1234.5"), parseNumberOrNull("1.234,5", DE))
        assertEquals(null, parseNumberOrNull("not a number", EN))
    }

    @Test
    fun everyLocaleAnswers() {
        var checked = 0
        for (locale in CldrNumber.supportedLocales) {
            val formatted = 1234567L.format(locale)
            assertTrue(formatted.isNotBlank(), "$locale formatted nothing")
            checked++
        }
        assertTrue(checked > 1000, "expected the full locale set, got $checked")
    }
}
