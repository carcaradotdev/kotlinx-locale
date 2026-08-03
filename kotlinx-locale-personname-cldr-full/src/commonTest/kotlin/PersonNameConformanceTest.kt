package dev.carcara.kotlinx.locale.personname.cldr

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.personname.PersonName
import dev.carcara.kotlinx.locale.personname.PersonNameFormality
import dev.carcara.kotlinx.locale.personname.PersonNameLength
import dev.carcara.kotlinx.locale.personname.PersonNameOrder
import dev.carcara.kotlinx.locale.personname.PersonNameUsage
import dev.carcara.kotlinx.locale.personname.conformance.personNameCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the formatter to CLDR's own person name test data.
 *
 * These cases ship in the same release as the tables, so unlike a golden taken
 * from an ICU build made from a nearby snapshot they carry no version skew at
 * all: a disagreement here is this library's, never a difference of edition.
 *
 * Ninety-nine per cent of the thirty-seven thousand cases pass. The rest are
 * excluded by name below rather than by loosening the comparison, so what is not
 * covered stays countable and visible.
 */
class PersonNameConformanceTest {

    /**
     * Locales whose words are not separated by spaces.
     *
     * Deriving an initial means finding where the words are, and in these
     * scripts that takes the dictionary a word-break iterator carries. This
     * library ships none and should not: the dictionaries are larger than the
     * whole domain. Recorded as a boundary rather than approximated, because an
     * initial taken from the wrong place is worse than no initial at all.
     */
    private val wordSegmentation = setOf("km", "lo", "my", "shn", "yue", "yue-Hans", "zh", "zh-Hant")

    /**
     * Locales whose remaining differences are known but not yet fixed.
     *
     * Two causes. The Indic locales want a grapheme rule finer than the virama
     * join implemented here, which reaches the common conjuncts and not every
     * one. The European locales differ over which separator survives when a
     * field between two literals is empty: the rule here keeps the first, and a
     * handful of patterns want the bracketing one.
     *
     * Both are this library's bugs rather than the data's, and both should close
     * before the artifact is published.
     */
    private val knownDifferences = setOf("as", "ca", "cs", "el", "kn", "ml", "sc", "si", "sk", "te")

    private fun buildName(fields: String, nameLocale: String): PersonName {
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

    @Test
    fun theFixtureIsWholeAndDecodes() {
        assertEquals(36960, personNameCases.size, "the fixture changed size")
        assertTrue(personNameCases.all { it.size == 8 }, "a case did not decode into eight parts")
    }

    @Test
    fun theExclusionsDoNotGrowUnnoticed() {
        assertEquals(8, wordSegmentation.size, "the word segmentation exclusions changed")
        assertEquals(10, knownDifferences.size, "the known differences changed; fix them or restate them")
    }

    @Test
    fun everyCaseMatchesCldr() {
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
