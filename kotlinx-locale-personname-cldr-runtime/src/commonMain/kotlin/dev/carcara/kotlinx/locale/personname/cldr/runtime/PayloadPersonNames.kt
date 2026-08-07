/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.personname.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.internal.ENTRY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.GraphemeClusters
import dev.carcara.kotlinx.locale.internal.KEY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.WordBreaks
import dev.carcara.kotlinx.locale.internal.resolvedRecord
import dev.carcara.kotlinx.locale.personname.PersonName
import dev.carcara.kotlinx.locale.personname.PersonNameFormality
import dev.carcara.kotlinx.locale.personname.PersonNameLength
import dev.carcara.kotlinx.locale.personname.PersonNameOrder
import dev.carcara.kotlinx.locale.personname.PersonNameSource
import dev.carcara.kotlinx.locale.personname.PersonNameUsage

/**
 * The grid, in the order the encoded record carries it.
 *
 * Forty-two rather than the fifty-four the four axes suggest: `sorting` is only
 * ever `referring`, because CLDR declares no sorted monogram and no sorted way
 * to address someone.
 */
private val CELLS: List<String> = buildList {
    for (order in listOf("givenFirst", "surnameFirst")) {
        for (length in listOf("long", "medium", "short")) {
            for (usage in listOf("referring", "addressing", "monogram")) {
                for (formality in listOf("formal", "informal")) add("$order.$length.$usage.$formality")
            }
        }
    }
    for (length in listOf("long", "medium", "short")) {
        for (formality in listOf("formal", "informal")) add("sorting.$length.referring.$formality")
    }
}

private val CELL_INDEX: Map<String, Int> = CELLS.withIndex().associate { (i, c) -> c to i }

/** Person name formatting over a table of CLDR-shaped records. */
@InternalKotlinxLocaleApi
public class PayloadPersonNames(private val records: Map<String, String>) : PersonNameSource {

    private val decoded = HashMap<String, PersonNameRecord?>()

    private fun recordFor(locale: Locale): PersonNameRecord? {
        val key = locale.toLanguageTag()
        if (key in decoded) return decoded[key]
        val built = resolvedRecord(records, locale)?.let(::PersonNameRecord)
        decoded[key] = built
        return built
    }

    override fun orderOrNull(nameLocale: Locale?, locale: Locale): PersonNameOrder? {
        val record = recordFor(locale) ?: return null
        return record.orderFor(nameLocale)
    }

    override fun formatOrNull(
        rawName: PersonName,
        length: PersonNameLength,
        usage: PersonNameUsage,
        formality: PersonNameFormality,
        order: PersonNameOrder,
        locale: Locale,
    ): String? {
        val record = recordFor(locale) ?: return null
        val name = rawName

        val effectiveOrder = when {
            order != PersonNameOrder.DEFAULT -> order
            name.preferredOrder != PersonNameOrder.DEFAULT -> name.preferredOrder
            else -> record.orderFor(name.locale)
        }
        val effectiveLength = if (length == PersonNameLength.DEFAULT) record.defaultLength else length
        val effectiveFormality = if (formality == PersonNameFormality.DEFAULT) record.defaultFormality else formality

        val cell = cellKey(effectiveOrder, effectiveLength, usage, effectiveFormality)
        val patterns = record.patternsFor(cell) ?: return null
        val pattern = bestPattern(patterns, name, record)
        val formatted = renderPattern(pattern, forPattern(pattern, name), record)

        // The replacement applies to the finished string rather than per literal,
        // which is what makes a Japanese family and given name join with nothing
        // between them while an internal space inside one field survives.
        val replacement = if (record.isNative(name.locale, locale)) record.nativeSpace else record.foreignSpace
        return if (replacement == " ") formatted else formatted.replace(" ", replacement)
    }

    /**
     * A mononym seen through one pattern.
     *
     * When a name has a given name and no surname, a pattern that asks for a
     * surname is given the one name to put there. The decision is per pattern
     * rather than made once for the name, which is the part that is easy to get
     * wrong: `{title} {surname}` has to render the mononym, and `{given-informal}`
     * in the very next cell has to render it too, so moving the value across for
     * both leaves one of them empty either way.
     *
     * Without this a one-word name formats to an initial where a pattern wanted
     * `{given-initial} {surname}`, or to nothing at all where it wanted a title
     * and a surname.
     */
    private fun forPattern(pattern: String, name: PersonName): PersonName {
        if (!fullSurname(name).isNullOrEmpty() || name.given.isNullOrEmpty()) return name
        // Only when the pattern has nowhere else to write the name. The test is
        // whether the pattern already spells the given name out, not whether it
        // writes a surname: Sardinian's short informal pattern is
        // `{given-informal} {surname-prefix} {surname-core-initial}`, which does
        // both, and keying on the surname moved the one name into a field that
        // then abbreviated it. A pattern that only initialises the given name is
        // not spelling it out, so `{given-initial} {surname}` still moves.
        val writesGiven = parsePattern(pattern).any {
            it is PatternElement.Field && it.name == "given" && "initial" !in it.modifiers
        }
        if (writesGiven) return name
        return PersonName(
            given = null,
            given2 = name.given2,
            surname = name.given,
            surname2 = name.surname2,
            title = name.title,
            generation = name.generation,
            credentials = name.credentials,
            givenInformal = null,
            surnamePrefix = name.surnamePrefix,
            surnameCore = name.surnameCore,
            locale = name.locale,
            preferredOrder = name.preferredOrder,
        )
    }

    /**
     * The whole surname, composed when only its parts were supplied.
     *
     * A caller who knows that `van den` is the prefix of `van den Hul` supplies
     * the two halves and never the whole, so `{surname}` has to put them back
     * together rather than answer with nothing.
     */
    private fun fullSurname(name: PersonName): String? {
        name.surname?.takeIf(String::isNotEmpty)?.let { return it }
        val parts = listOfNotNull(name.surnamePrefix, name.surnameCore).filter(String::isNotEmpty)
        return parts.joinToString(" ").takeIf(String::isNotEmpty)
    }

    private fun cellKey(order: PersonNameOrder, length: PersonNameLength, usage: PersonNameUsage, formality: PersonNameFormality): String {
        val orderName = when (order) {
            PersonNameOrder.SURNAME_FIRST -> "surnameFirst"
            PersonNameOrder.SORTING -> "sorting"
            else -> "givenFirst"
        }
        val lengthName = when (length) {
            PersonNameLength.SHORT -> "short"
            PersonNameLength.LONG -> "long"
            else -> "medium"
        }
        // `sorting` has no addressing or monogram cell. CLDR does not declare
        // one, so the request is answered from the given-first grid rather than
        // resolving to nothing.
        val usageName = when (usage) {
            PersonNameUsage.ADDRESSING -> "addressing"
            PersonNameUsage.MONOGRAM -> "monogram"
            PersonNameUsage.REFERRING -> "referring"
        }
        val formalityName = if (formality == PersonNameFormality.INFORMAL) "informal" else "formal"
        if (orderName == "sorting" && usageName != "referring") {
            return "givenFirst.$lengthName.$usageName.$formalityName"
        }
        return "$orderName.$lengthName.$usageName.$formalityName"
    }

    /**
     * The pattern that says the most about this particular name.
     *
     * Most populated placeholders wins; then fewest empty ones. That second term
     * is what stops a pattern with three empty slots beating one with none when
     * both fill the same two fields.
     */
    private fun bestPattern(patterns: List<String>, name: PersonName, record: PersonNameRecord): String {
        if (patterns.size == 1) return patterns[0]
        var best = patterns[0]
        var bestPopulated = -1
        var bestEmpty = Int.MAX_VALUE
        for (pattern in patterns) {
            var populated = 0
            var empty = 0
            for (element in parsePattern(pattern)) {
                if (element !is PatternElement.Field) continue
                if (resolveField(element, forPattern(pattern, name), record).isNullOrEmpty()) empty++ else populated++
            }
            if (populated > bestPopulated || (populated == bestPopulated && empty < bestEmpty)) {
                best = pattern
                bestPopulated = populated
                bestEmpty = empty
            }
        }
        return best
    }

    /**
     * Renders a pattern, dropping the separators around fields that are absent.
     *
     * The rule that matters: a literal is held back until a populated field
     * follows it, and discarded when an empty one does. That is what turns
     * `{title} {surname}` with no title into `Adler` rather than into a string
     * with a leading space, and what leaves no trailing comma when the last
     * field is missing.
     */
    /**
     * Renders a pattern, dropping what UTS #35 Part 8 says an absent field takes
     * with it.
     *
     * The specification gives this as numbered steps and they are followed in
     * its order rather than approximated. Getting it wrong is not subtle in its
     * effect but is very subtle in its cause: the earlier version kept the first
     * literal after an empty field, which lost a comma in Catalan and left an
     * unbalanced parenthesis in Czech.
     *
     * 1. Everything before the first populated field is dropped, and everything
     *    after the last one.
     * 2. A run of two or more empty fields separated only by literals loses the
     *    fields and the literals between them. A single empty field is removed.
     *    What survives is the literal before the run and the literal after it.
     * 3. The two literals now adjacent are coalesced: if either is empty the
     *    answer is the other, and if the second matches the end of the first the
     *    answer is the first. That is what turns a space and a space into one
     *    space rather than two.
     */
    private fun renderPattern(pattern: String, name: PersonName, record: PersonNameRecord): String {
        val result = StringBuilder()
        // The literal run collected before the current gap and the one collected
        // inside it. Splitting them is what lets step 3 splice the two sides
        // together rather than pick one and throw the other away.
        val textBefore = StringBuilder()
        val textAfter = StringBuilder()
        var seenLeadingField = false
        var seenEmptyLeadingField = false
        var seenEmptyField = false

        for (element in parsePattern(pattern)) {
            when (element) {
                is PatternElement.Literal -> when {
                    // Before the first populated field: nothing to attach to.
                    seenEmptyLeadingField -> Unit
                    seenEmptyField -> textAfter.append(element.text)
                    else -> textBefore.append(element.text)
                }
                is PatternElement.Field -> {
                    val value = resolveField(element, name, record)
                    if (value.isNullOrEmpty()) {
                        if (!seenLeadingField) {
                            seenEmptyLeadingField = true
                            textBefore.setLength(0)
                        } else {
                            seenEmptyField = true
                            textAfter.setLength(0)
                        }
                    } else {
                        seenLeadingField = true
                        seenEmptyLeadingField = false
                        if (seenEmptyField) {
                            result.append(coalesce(textBefore, textAfter))
                            seenEmptyField = false
                        } else {
                            result.append(textBefore)
                            textBefore.setLength(0)
                        }
                        result.append(value)
                    }
                }
            }
        }
        // A literal run left over after the last populated field belongs to the
        // fields that followed it, and those were all empty.
        if (!seenEmptyField) result.append(textBefore)
        return result.toString()
    }

    /**
     * Step 3 of the pattern process: the literal run around a gap.
     *
     * Keeps [before] up to and including its first space, and the run of
     * non-space characters at the end of [after]. Both sides survive, which is
     * the part a rule that picks one of them gets wrong: the Czech and Slovak
     * sorting patterns end `{given2} {surname-prefix} ({title}, {credentials})`,
     * and with no prefix to write the bracket lives in the half that a
     * keep-the-first rule discards.
     *
     * Ported from `PersonNamePattern.coalesce` in ICU rather than derived from
     * UTS #35 Part 8, which states the rule in one sentence.
     */
    private fun coalesce(before: StringBuilder, after: StringBuilder): String {
        if (endsWith(before, after)) after.setLength(0)

        var end = 0
        while (end < before.length && !before[end].isWhitespace()) end++
        var start = after.length - 1
        while (start >= 0 && !after[start].isWhitespace()) start--
        // One space joins the two halves: the first one on the left if there is
        // one, otherwise the last one on the right.
        if (end < before.length) {
            end++
        } else if (start >= 0) {
            start--
        }

        val joined = before.substring(0, end) + after.substring(start + 1)
        before.setLength(0)
        after.setLength(0)
        return joined
    }

    /** Whether [text] ends with [suffix], neither of them a `String` yet. */
    private fun endsWith(text: StringBuilder, suffix: StringBuilder): Boolean {
        var textIndex = text.length - 1
        var suffixIndex = suffix.length - 1
        while (textIndex >= 0 && suffixIndex >= 0 && text[textIndex] == suffix[suffixIndex]) {
            textIndex--
            suffixIndex--
        }
        return suffixIndex < 0
    }

    private fun resolveField(field: PatternElement.Field, name: PersonName, record: PersonNameRecord): String? {
        val modifiers = field.modifiers
        var value: String? = when (field.name) {
            "title" -> name.title
            "given" -> if ("informal" in modifiers) name.givenInformal ?: name.given else name.given
            "given2" -> name.given2
            "surname" -> when {
                "core" in modifiers -> name.surnameCore ?: name.surname
                "prefix" in modifiers -> name.surnamePrefix
                else -> fullSurname(name)
            }
            "surname2" -> name.surname2
            "generation" -> name.generation
            "credentials" -> name.credentials
            else -> null
        }
        if (value.isNullOrEmpty()) return value

        if ("monogram" in modifiers) {
            value = firstGrapheme(value)
        } else if ("initial" in modifiers) {
            value = initialsOf(value, record, retain = "retain" in modifiers)
        }
        if ("allCaps" in modifiers) value = value.uppercase().withoutGreekTonos()
        if ("initialCap" in modifiers) value = value.replaceFirstChar(Char::uppercaseChar)
        return value
    }

    /**
     * The initials of a value, wrapped and joined the way the locale writes them.
     *
     * Words are found from the characters rather than with a full break
     * iterator: UAX #29's rules that need no dictionary are applied, and the
     * ones that do are not. The difference shows only where words are not
     * separated by spaces, which is Khmer, Burmese and the CJK locales. See the
     * conformance test for the exact set that excludes.
     */
    private fun initialsOf(value: String, record: PersonNameRecord, retain: Boolean): String {
        // Split on anything that is not a letter, not only on spaces. A
        // hyphenated given name is two words for this purpose: it initializes to
        // two letters, not one, in every locale CLDR has data for.
        //
        // Walked by grapheme cluster rather than by char, so a cluster is never
        // split down the middle. That is what a format character inside a word
        // needs: Malayalam writes a zero-width non-joiner inside `സ്‌റ്റോബർ`,
        // which UAX #29 classes as Extend, so it belongs to the cluster and not
        // between two words. Taking it for a separator produced two initials
        // where CLDR produces one.
        val words = ArrayList<String>()
        val separators = ArrayList<String>()
        val word = StringBuilder()
        val separator = StringBuilder()
        val clusters = GraphemeClusters.clusters(value)
        for ((index, cluster) in clusters.withIndex()) {
            val ch = cluster[0]
            val letter = ch.isLetterOrDigit() || ch.isMark()
            // UAX #29 rules WB6 and WB7: mid-word punctuation standing between
            // two letters does not end the word. Catalan writes `Gal·la` with a
            // middle dot and takes one initial from it, not two.
            val midWord = !letter &&
                word.isNotEmpty() &&
                separator.isEmpty() &&
                WordBreaks.isMidWord(ch) &&
                clusters.getOrNull(index + 1)?.get(0)?.let { it.isLetterOrDigit() || it.isMark() } == true
            if (letter || midWord) {
                if (separator.isNotEmpty()) {
                    if (word.isNotEmpty()) {
                        words.add(word.toString())
                        separators.add(separator.toString())
                        word.clear()
                    }
                    separator.clear()
                }
                word.append(cluster)
            } else {
                separator.append(cluster)
            }
        }
        if (word.isNotEmpty()) words.add(word.toString())
        if (words.isEmpty()) return ""

        val initials = words.map { substitute(record.initialPattern, firstGrapheme(it)) }
        if (!retain) return initials.reduce { acc, next -> substitute(record.initialSequence, acc, next) }

        // With `retain`, the punctuation between the words is reproduced rather
        // than replaced by the sequence pattern, so a hyphenated name keeps its
        // hyphen instead of becoming two loose initials.
        return buildString {
            append(initials[0])
            for (index in 1 until initials.size) {
                append(separators.getOrElse(index - 1) { " " })
                append(initials[index])
            }
        }
    }

    public companion object
}

/** One locale's person name data. */
@InternalKotlinxLocaleApi
public class PersonNameRecord(record: String) {

    private val fields = record.split(FIELD_SEPARATOR)

    private val cells: List<List<String>> = fields.getOrNull(0)
        ?.split(ENTRY_SEPARATOR)
        ?.map { it.split(KEY_SEPARATOR).filter(String::isNotEmpty) }
        .orEmpty()

    private val givenFirstLocales: Set<String> = fields.getOrNull(1).orEmpty().split(' ').filterNotTo(HashSet()) { it.isBlank() }
    private val surnameFirstLocales: Set<String> = fields.getOrNull(2).orEmpty().split(' ').filterNotTo(HashSet()) { it.isBlank() }

    public val defaultLength: PersonNameLength = when (fields.getOrNull(3)) {
        "short" -> PersonNameLength.SHORT
        "long" -> PersonNameLength.LONG
        else -> PersonNameLength.MEDIUM
    }

    public val defaultFormality: PersonNameFormality =
        if (fields.getOrNull(4) == "informal") PersonNameFormality.INFORMAL else PersonNameFormality.FORMAL

    public val nativeSpace: String = fields.getOrNull(5) ?: " "
    public val foreignSpace: String = fields.getOrNull(6) ?: " "
    public val initialPattern: String = fields.getOrNull(7)?.takeIf(String::isNotEmpty) ?: "{0}."
    public val initialSequence: String = fields.getOrNull(8)?.takeIf(String::isNotEmpty) ?: "{0} {1}"

    internal fun patternsFor(cell: String): List<String>? = CELL_INDEX[cell]?.let { cells.getOrNull(it) }?.takeIf { it.isNotEmpty() }

    /**
     * Which order this locale writes a name from [nameLocale] in.
     *
     * The lists name the *name's* locale, not the reader's, which is the part
     * that is easy to get backwards: English lists Japanese and Korean as
     * surname first and says nothing about Hungarian, while Hungarian lists
     * itself. So a Hungarian name is surname first in Hungarian and given first
     * in English, and both are right.
     */
    internal fun orderFor(nameLocale: Locale?): PersonNameOrder {
        val language = nameLocale?.language ?: "und"
        return when {
            language in surnameFirstLocales -> PersonNameOrder.SURNAME_FIRST
            language in givenFirstLocales -> PersonNameOrder.GIVEN_FIRST
            "und" in surnameFirstLocales -> PersonNameOrder.SURNAME_FIRST
            else -> PersonNameOrder.GIVEN_FIRST
        }
    }

    /**
     * Whether the name reads as native to the formatting locale.
     *
     * Compared by language alone, with Japanese and Chinese counting as matching
     * each other, which is what the specification's own implementation does. A
     * null name locale is treated as native, because there is nothing to say it
     * is foreign.
     */
    internal fun isNative(nameLocale: Locale?, formattingLocale: Locale): Boolean {
        val name = nameLocale?.language ?: return true
        val formatting = formattingLocale.language
        if (name == formatting) return true
        return (name == "ja" || name == "zh") && (formatting == "ja" || formatting == "zh")
    }

    public companion object
}

internal sealed interface PatternElement {
    data class Literal(val text: String) : PatternElement
    data class Field(val name: String, val modifiers: Set<String>) : PatternElement
}

/** Splits `{given-informal} {surname}` into its literals and its fields. */
internal fun parsePattern(pattern: String): List<PatternElement> {
    val elements = ArrayList<PatternElement>()
    val literal = StringBuilder()
    var index = 0
    while (index < pattern.length) {
        val ch = pattern[index]
        if (ch == '{') {
            val close = pattern.indexOf('}', index)
            if (close < 0) {
                literal.append(ch)
                index++
                continue
            }
            if (literal.isNotEmpty()) {
                elements.add(PatternElement.Literal(literal.toString()))
                literal.clear()
            }
            val parts = pattern.substring(index + 1, close).split('-')
            elements.add(PatternElement.Field(parts[0], parts.drop(1).toSet()))
            index = close + 1
        } else {
            literal.append(ch)
            index++
        }
    }
    if (literal.isNotEmpty()) elements.add(PatternElement.Literal(literal.toString()))
    return elements
}

/** `{0}` and `{1}` substitution, for the initial and initial-sequence patterns. */
internal fun substitute(template: String, vararg arguments: String): String {
    val result = StringBuilder(template.length + 8)
    var index = 0
    while (index < template.length) {
        val ch = template[index]
        if (ch == '{' && index + 2 < template.length && template[index + 2] == '}') {
            val slot = template[index + 1] - '0'
            if (slot in arguments.indices) {
                result.append(arguments[slot])
                index += 3
                continue
            }
        }
        result.append(ch)
        index++
    }
    return result.toString()
}

internal fun Char.isMark(): Boolean = when (category) {
    CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK -> true
    else -> false
}

/**
 * The first grapheme cluster of a value, per UAX #29.
 *
 * A monogram is one written unit, and in Bengali or Devanagari that unit is a
 * consonant bound to the next by a virama. This used to be a hand-written rule
 * that joined across any virama, which was wrong in both directions: it took a
 * cluster too many in Kannada and one too few in Telugu. It now defers to the
 * algorithm, which is held to Unicode's own conformance file.
 */
internal fun firstGrapheme(value: String): String = GraphemeClusters.firstCluster(value)

/**
 * Greek written in capitals drops its accents.
 *
 * `uppercase()` maps alpha with tonos to capital alpha with tonos, because that
 * character exists. Greek orthography does not use it: a name set in capitals is
 * written without the accent, so a monogram of `Άννα` is `Α` and not `Ά`. The
 * dialytika stays, since it marks a separate vowel rather than stress.
 */
internal fun String.withoutGreekTonos(): String {
    if (none { it in GREEK_TONOS }) return this
    return map { GREEK_TONOS[it] ?: it }.joinToString("")
}

/** The accented Greek capitals `uppercase()` can produce, and what they are written as. */
private val GREEK_TONOS: Map<Char, Char> = mapOf(
    '\u0386' to '\u0391', // Ά -> Α
    '\u0388' to '\u0395', // Έ -> Ε
    '\u0389' to '\u0397', // Ή -> Η
    '\u038A' to '\u0399', // Ί -> Ι
    '\u038C' to '\u039F', // Ό -> Ο
    '\u038E' to '\u03A5', // Ύ -> Υ
    '\u038F' to '\u03A9', // Ώ -> Ω
)
