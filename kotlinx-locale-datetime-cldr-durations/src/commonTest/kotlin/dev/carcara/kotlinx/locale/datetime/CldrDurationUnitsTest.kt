package dev.carcara.kotlinx.locale.datetime

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.durations.CldrDurationUnits
import dev.carcara.kotlinx.locale.datetime.cldr.durations.durationFormat
import dev.carcara.kotlinx.locale.datetime.cldr.durations.durationUnitName
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.DurationUnit
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.UnitWidth
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue

private val en = Locale.forLanguageTag("en")
private val de = Locale.forLanguageTag("de")
private val cs = Locale.forLanguageTag("cs")
private val ar = Locale.forLanguageTag("ar")
private val fr = Locale.forLanguageTag("fr")
private val es = Locale.forLanguageTag("es")

/**
 * Which space a locale puts between the count and the word is data rather than
 * typography, so every separator below is written as an escape. A literal
 * U+00A0 is invisible in a diff and reads as an ordinary space to anyone
 * copying the line.
 */
private const val NBSP = "\u00A0"

val CldrDurationUnitsTest by matrixSuite {

    test("widthsDiffer") {
        assertEquals("2 hours", durationFormat(2, DurationUnit.HOUR, UnitWidth.LONG, en))
        assertEquals("2 hr", durationFormat(2, DurationUnit.HOUR, UnitWidth.SHORT, en))
        assertEquals("2h", durationFormat(2, DurationUnit.HOUR, UnitWidth.NARROW, en))

        assertEquals("2 minutes", durationFormat(2, DurationUnit.MINUTE, UnitWidth.LONG, en))
        assertEquals("2 min", durationFormat(2, DurationUnit.MINUTE, UnitWidth.SHORT, en))
        assertEquals("2m", durationFormat(2, DurationUnit.MINUTE, UnitWidth.NARROW, en))
    }

    test("singularTakesTheOneForm") {
        assertEquals("1 hour", durationFormat(1, DurationUnit.HOUR, UnitWidth.LONG, en))
        assertEquals("1 minute", durationFormat(1, DurationUnit.MINUTE, UnitWidth.LONG, en))
    }

    /** French joins its hours with U+00A0 where German uses an ordinary space. */
    test("otherLocalesUseTheirOwnWording") {
        assertEquals("2 Stunden", durationFormat(2, DurationUnit.HOUR, UnitWidth.LONG, de))
        assertEquals("2${NBSP}heures", durationFormat(2, DurationUnit.HOUR, UnitWidth.LONG, fr))
    }

    /**
     * French declares no long wording for nights, only short, and CLDR's root has
     * no long block at all. ICU answers in French rather than falling through to
     * English, so a missing long resolves out of the locale's own short. Note the
     * ordinary space, where the same locale's hours take U+00A0.
     */
    test("aMissingLongReadsTheLocalesOwnShort") {
        assertEquals("3 nuits", durationFormat(3, DurationUnit.NIGHT, UnitWidth.LONG, fr))
    }

    /**
     * Spanish declares no short hour and root does, so the short form is root's
     * `{0} h` rather than Spanish's own narrow `3h` or its long `3 horas`.
     */
    test("aMissingShortReadsRoot") {
        assertEquals("3 h", durationFormat(3, DurationUnit.HOUR, UnitWidth.SHORT, es))
    }

    /** A locale CLDR has no unit wording for falls through to English, as ICU does. */
    test("aLocaleWithNoWordingFallsBackToEnglish") {
        val afar = Locale.forLanguageTag("aa")
        assertEquals("2 hours", durationFormat(2, DurationUnit.HOUR, UnitWidth.LONG, afar))
        assertEquals("2 hr", durationFormat(2, DurationUnit.HOUR, UnitWidth.SHORT, afar))
        assertEquals("2h", durationFormat(2, DurationUnit.HOUR, UnitWidth.NARROW, afar))
    }

    /**
     * Serbian writes four grammatical cases of the same pattern and only the
     * caseless one is the citation form. Taking whichever came first in the file
     * gave the genitive.
     */
    test("grammaticalCaseVariantsAreNotTheAnswer") {
        assertEquals("2 сата", durationFormat(2, DurationUnit.HOUR, UnitWidth.LONG, Locale.forLanguageTag("sr")))
    }

    /**
     * The reason the entry point takes a [Decimal]. Czech puts a value written
     * with a fraction digit in `many` whatever the value is, so `1` and `1.0`
     * differ for the same quantity.
     */
    test("czechSeparatesOneFromOnePointZero") {
        val one = durationFormat(1, DurationUnit.HOUR, UnitWidth.LONG, cs)
        val onePointZero = durationFormat(1.0, fractionDigits = 1, DurationUnit.HOUR, UnitWidth.LONG, cs)
        assertTrue(one != onePointZero, "expected 1 and 1.0 to take different plural forms, both were $one")
    }

    /**
     * `{0}` goes through the number formatter rather than being pasted in, which
     * is what puts a locale's own digits in the phrase. Egyptian Arabic resolves
     * to the Arabic-Indic set; plain `ar` does not, and ICU agrees.
     */
    test("digitsFollowTheLocalesNumberingSystem") {
        val egyptian = durationFormat(3, DurationUnit.HOUR, UnitWidth.LONG, Locale.forLanguageTag("ar-EG"))
        assertTrue(egyptian.any { it in '٠'..'٩' }, "expected Arabic-Indic digits, got $egyptian")
        assertTrue(durationFormat(3, DurationUnit.HOUR, UnitWidth.LONG, ar).any { it in '0'..'9' })
    }

    /** The digits printed are the digits the plural form was chosen from. */
    test("fractionsKeepTheirDigits") {
        assertEquals("1.5 hours", durationFormat(1.5, fractionDigits = 1, DurationUnit.HOUR, UnitWidth.LONG, en))
        assertEquals("2.50 hours", durationFormat(Decimal.ofUnscaled(250, 2), DurationUnit.HOUR, UnitWidth.LONG, en))
    }

    test("unitNamesResolve") {
        assertEquals("hours", durationUnitName(DurationUnit.HOUR, UnitWidth.LONG, en))
        assertEquals("minutes", durationUnitName(DurationUnit.MINUTE, UnitWidth.LONG, en))
        assertEquals("min", durationUnitName(DurationUnit.MINUTE, UnitWidth.NARROW, en))
    }

    test("everyUnitAnswersInEveryWidthForASampleOfLocales") {
        for (tag in listOf("en", "de", "fr", "es", "ru", "ja", "zh", "ar", "hi", "pl", "cs", "tr")) {
            val locale = Locale.forLanguageTag(tag)
            for (unit in DurationUnit.entries) {
                for (width in UnitWidth.entries) {
                    val text = CldrDurationUnits.durationFormatOrNull(Decimal.of(3), unit, width, locale)
                    assertTrue(text != null && text.isNotEmpty(), "no wording for $unit/$width in $tag")
                    assertTrue("{0}" !in text, "placeholder survived for $unit/$width in $tag: $text")
                }
            }
        }
    }

    /** Every locale the table claims should answer for every unit and width. */
    test("everyLocaleInTheTableAnswers") {
        for (locale in CldrDurationUnits.supportedLocales) {
            for (unit in DurationUnit.entries) {
                for (width in UnitWidth.entries) {
                    val text = CldrDurationUnits.durationFormatOrNull(Decimal.of(2), unit, width, locale)
                    assertTrue(text != null && text.isNotEmpty(), "no $unit/$width wording for $locale")
                    assertTrue("{0}" !in text, "placeholder survived for $unit/$width in $locale: $text")
                }
            }
        }
    }

    /** The table carries the locales CLDR has real wording for, not all 1122. */
    test("supportedLocalesReflectsRealCoverage") {
        val supported = CldrDurationUnits.supportedLocales
        assertTrue(supported.size in 500..900, "expected a few hundred locales, got ${supported.size}")
        assertTrue(Locale.forLanguageTag("en") in supported)
        assertTrue(Locale.forLanguageTag("aa") !in supported, "aa has no CLDR unit wording and should not be claimed")
    }
}
