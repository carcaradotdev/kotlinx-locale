package dev.carcara.kotlinx.locale.personname.cldr

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.personname.PersonName
import dev.carcara.kotlinx.locale.personname.PersonNameFormality
import dev.carcara.kotlinx.locale.personname.PersonNameLength
import dev.carcara.kotlinx.locale.personname.PersonNameOrder
import dev.carcara.kotlinx.locale.personname.PersonNameUsage
import dev.carcara.kotlinx.locale.personname.conformance.personNameCases
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * Holds the formatter to CLDR's own person name test data.
 *
 * These cases ship in the same release as the tables, so unlike a golden taken
 * from an ICU build made from a nearby snapshot they carry no version skew at
 * all: a disagreement here is this library's, never a difference of edition.
 *
 * Every one of the thirty-seven thousand cases passes, apart from the locales
 * whose words are not separated by spaces. Those are excluded by name below
 * rather than by loosening the comparison, so what is not covered stays
 * countable and visible.
 */
val PersonNameConformanceTest by matrixSuite {

    /**
     * Locales whose words are not separated by spaces.
     *
     * Deriving an initial means finding where the words are, and in these
     * scripts that takes the dictionary a word-break iterator carries. This
     * library ships none and should not: the dictionaries are larger than the
     * whole domain. Recorded as a boundary rather than approximated, because an
     * initial taken from the wrong place is worse than no initial at all.
     */
    val wordSegmentation = setOf("km", "lo", "my", "shn", "yue", "yue-Hans", "zh", "zh-Hant")

    /**
     * Locales whose remaining differences are known but not yet fixed.
     *
     * Empty, and kept rather than deleted so that it cannot fill up quietly.
     * Every locale outside [wordSegmentation] agrees with CLDR on every case.
     *
     * It held six until the rules behind them were read out of ICU rather than
     * inferred from UTS #35 Part 8, which states them tersely enough that three
     * separate answers came out wrong: which literal survives around an empty
     * field, when a lone given name moves into the surname, and where a word
     * ends when the punctuation inside it is a middle dot. `PersonNamePattern`
     * and `FieldModifierImpl` in the ICU checkout are the reference.
     */
    val knownDifferences = setOf<String>()

    fun buildName(fields: String, nameLocale: String): PersonName {
        val values = HashMap<String, String>()
        if (fields.isNotEmpty()) {
            for (pair in fields.split('')) {
                val equals = pair.indexOf('=')
                if (equals > 0) values[pair.substring(0, equals)] = pair.substring(equals + 1)
            }
        }
        return PersonName(
            given = values["given"],
            given2 = values["given2"],
            surname = values["surname"],
            surname2 = values["surname2"],
            title = values["title"],
            generation = values["generation"],
            credentials = values["credentials"],
            givenInformal = values["given-informal"],
            surnamePrefix = values["surname-prefix"],
            surnameCore = values["surname-core"],
            locale = nameLocale.takeIf(String::isNotEmpty)?.let(Locale::forLanguageTag),
        )
    }

    test("theFixtureIsWholeAndDecodes") {
        assertEquals(36960, personNameCases.size, "the fixture changed size")
        assertTrue(personNameCases.all { it.size == 8 }, "a case did not decode into eight parts")
    }

    test("theExclusionsDoNotGrowUnnoticed") {
        assertEquals(8, wordSegmentation.size, "the word segmentation exclusions changed")
        assertEquals(0, knownDifferences.size, "the known differences changed; fix them or restate them")
    }

    test("everyCaseMatchesCldr") {
        val mismatches = ArrayList<String>()
        var compared = 0
        var excluded = 0

        for (case in personNameCases) {
            val locale = case[0]
            if (locale in wordSegmentation || locale in knownDifferences) {
                excluded++
                continue
            }

            val actual = personNameFormat(
                name = buildName(case[1], case[2]),
                length = when (case[4]) {
                    "short" -> PersonNameLength.SHORT
                    "long" -> PersonNameLength.LONG
                    else -> PersonNameLength.MEDIUM
                },
                usage = when (case[5]) {
                    "addressing" -> PersonNameUsage.ADDRESSING
                    "monogram" -> PersonNameUsage.MONOGRAM
                    else -> PersonNameUsage.REFERRING
                },
                formality = if (case[6] == "informal") PersonNameFormality.INFORMAL else PersonNameFormality.FORMAL,
                order = when (case[3]) {
                    "surnameFirst" -> PersonNameOrder.SURNAME_FIRST
                    "sorting" -> PersonNameOrder.SORTING
                    else -> PersonNameOrder.GIVEN_FIRST
                },
                locale = Locale.forLanguageTag(locale),
            )
            compared++
            if (actual != case[7]) {
                mismatches += "$locale [${case[3]}/${case[4]}/${case[5]}/${case[6]}] ${case[1]}: " +
                    "expected '${case[7]}', got '$actual'"
            }
        }

        assertTrue(compared > 28_000, "the fixture shrank to $compared comparisons, with $excluded excluded")
        assertTrue(
            mismatches.isEmpty(),
            "${mismatches.size} of $compared disagree with CLDR across " +
                "${mismatches.map { it.substringBefore(' ') }.toSet().size} locales:\n" +
                mismatches.take(30).joinToString("\n"),
        )
    }
}
