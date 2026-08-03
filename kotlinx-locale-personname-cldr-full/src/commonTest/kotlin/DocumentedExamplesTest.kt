package dev.carcara.kotlinx.locale.personname.cldr

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.personname.PersonName
import dev.carcara.kotlinx.locale.personname.PersonNameLength
import dev.carcara.kotlinx.locale.personname.PersonNameOrder
import dev.carcara.kotlinx.locale.personname.PersonNameUsage
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every person name example in API.md, asserted so the prose cannot drift. */
class DocumentedExamplesTest {

    private val name = PersonName(given = "Iris", surname = "Adler")

    @Test
    fun theFormattingExamples() {
        val en = Locale.forLanguageTag("en")
        assertEquals("Iris Adler", personNameFormat(name, locale = en))
        // English defaults to a medium, informal monogram, which is one letter.
        assertEquals("I", personNameFormat(name, usage = PersonNameUsage.MONOGRAM, locale = en))
        assertEquals(
            "IA",
            personNameFormat(name, length = PersonNameLength.LONG, usage = PersonNameUsage.MONOGRAM, locale = en),
        )
        assertEquals("Adler, Iris", personNameFormat(name, order = PersonNameOrder.SORTING, locale = en))
    }

    @Test
    fun theOrderExamples() {
        val hu = Locale.forLanguageTag("hu")
        val en = Locale.forLanguageTag("en")
        assertEquals(PersonNameOrder.SURNAME_FIRST, personNameOrder(hu, hu))
        assertEquals(PersonNameOrder.GIVEN_FIRST, personNameOrder(hu, en))
    }

    @Test
    fun aOnePartNameIsWrittenOutRatherThanAbbreviated() {
        val en = Locale.forLanguageTag("en")
        assertEquals("Zendaya", personNameFormat(PersonName(given = "Zendaya"), locale = en))
    }
}
