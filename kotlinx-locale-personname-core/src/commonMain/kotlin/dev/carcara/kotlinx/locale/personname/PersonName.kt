package dev.carcara.kotlinx.locale.personname

import dev.carcara.kotlinx.locale.Locale

/** How much of a name is written out. */
public enum class PersonNameLength {
    SHORT,
    MEDIUM,
    LONG,

    /** Whatever the formatting locale declares, which is not the same everywhere. */
    DEFAULT,
    ;

    public companion object
}

/** What the formatted name is for. */
public enum class PersonNameUsage {
    /** Talking to the person: `Ms. Adler`. */
    ADDRESSING,

    /** Talking about the person: `Iris Adler`. */
    REFERRING,

    /** Initials, for an avatar or a compact badge. */
    MONOGRAM,
    ;

    public companion object
}

/** How formal the wording is. */
public enum class PersonNameFormality {
    FORMAL,
    INFORMAL,

    /** Whatever the formatting locale declares. */
    DEFAULT,
    ;

    public companion object
}

/**
 * Which part of the name comes first.
 *
 * Not a property of the name and not a property of the reader, but of the pair.
 * CLDR gives each formatting locale the set of name locales it writes surname
 * first, so a Hungarian name is written surname first in Hungarian and given
 * first in English, and neither is a mistake.
 */
public enum class PersonNameOrder {
    /** Let the formatting locale decide, given the name's own locale. */
    DEFAULT,
    GIVEN_FIRST,
    SURNAME_FIRST,

    /** The form a list is sorted by: `Adler, Iris`. */
    SORTING,
    ;

    public companion object
}

/**
 * A person's name, in the parts UTS #35 Part 8 defines.
 *
 * Every part is optional, because real names are not all the same shape, and the
 * formatter's job is to write whatever is present the way the locale writes it.
 * A name with only [given] formats to that one word rather than to a string with
 * stray punctuation where the missing parts were.
 *
 * The modifiers the specification defines are split between what a caller
 * supplies and what the formatter derives. Initials, monograms and capitalized
 * forms are derived, because they are a function of the value and the locale's
 * own patterns. The informal given name, the surname prefix and the surname core
 * are supplied, because no algorithm can recover them: only the bearer knows
 * that `van den` is the prefix of `van den Hul` or that `Bob` stands in for
 * `Robert`.
 */
public class PersonName(
    public val given: String? = null,
    public val given2: String? = null,
    public val surname: String? = null,
    public val surname2: String? = null,
    public val title: String? = null,
    public val generation: String? = null,
    public val credentials: String? = null,
    /** What `{given-informal}` asks for; [given] answers when this is absent. */
    public val givenInformal: String? = null,
    /** The `van den` of `van den Hul`; absent means the surname has no prefix. */
    public val surnamePrefix: String? = null,
    /** The `Hul` of `van den Hul`; [surname] answers when this is absent. */
    public val surnameCore: String? = null,
    /**
     * The locale this name is from, which decides the order and which space
     * joins its parts.
     *
     * Null means "treat it as native to whoever is reading", which is the right
     * assumption when a name arrived without provenance and the wrong one to
     * make silently, so it is worth passing when it is known.
     */
    public val locale: Locale? = null,
    /** Overrides the order the locale pair would otherwise choose. */
    public val preferredOrder: PersonNameOrder = PersonNameOrder.DEFAULT,
) {
    override fun toString(): String = "PersonName(given=$given, surname=$surname, locale=$locale)"

    public companion object
}

/** A source of person name formatting. */
public interface PersonNameSource {

    /** [name] written for [locale], or null when this build carries no data for it. */
    public fun formatOrNull(
        name: PersonName,
        length: PersonNameLength,
        usage: PersonNameUsage,
        formality: PersonNameFormality,
        order: PersonNameOrder,
        locale: Locale,
    ): String?

    /**
     * Which order [locale] writes a name from [nameLocale] in.
     *
     * Only ever [PersonNameOrder.GIVEN_FIRST] or [PersonNameOrder.SURNAME_FIRST];
     * null when this build has no data for [locale].
     */
    public fun orderOrNull(nameLocale: Locale?, locale: Locale): PersonNameOrder?

    public companion object
}

/**
 * [name] written for [locale], falling back to the given name and surname joined
 * by a space.
 *
 * The fallback is deliberately dumb rather than clever: with no data there is
 * nothing to base an order on, and inventing one would be wrong half the time.
 */
public fun PersonNameSource.format(
    name: PersonName,
    length: PersonNameLength = PersonNameLength.DEFAULT,
    usage: PersonNameUsage = PersonNameUsage.REFERRING,
    formality: PersonNameFormality = PersonNameFormality.DEFAULT,
    order: PersonNameOrder = PersonNameOrder.DEFAULT,
    locale: Locale = Locale.current,
): String = formatOrNull(name, length, usage, formality, order, locale)
    ?: listOfNotNull(name.given, name.surname).joinToString(" ")

/** Which order [locale] writes a name from [nameLocale] in; given first when unknown. */
public fun PersonNameSource.order(nameLocale: Locale?, locale: Locale = Locale.current): PersonNameOrder =
    orderOrNull(nameLocale, locale) ?: PersonNameOrder.GIVEN_FIRST
