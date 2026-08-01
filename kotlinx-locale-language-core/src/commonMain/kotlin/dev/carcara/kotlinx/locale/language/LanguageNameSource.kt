package dev.carcara.kotlinx.locale.language

import dev.carcara.kotlinx.locale.Capitalization
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource

/** Which spelling of a name: CLDR's default, or its shorter `alt="short"` form. */
public enum class LanguageNameStyle { STANDARD, SHORT }

/**
 * Whether a dialect gets its own name or is composed from the language plus its
 * subtags.
 *
 * [DIALECT] gives `British English`, [STANDARD] gives
 * `English (United Kingdom)`. This is UTS #35's combine-language parameter and
 * ECMA-402's `languageDisplay`, and [DIALECT] is the default in both.
 */
public enum class LanguageDisplay { DIALECT, STANDARD }

/**
 * The three patterns that join the parts of a locale display name.
 *
 * They are locale data rather than punctuation: a locale decides both the
 * bracket style and the separator.
 */
public class LocaleDisplayPatterns(
    /** `{0} ({1})`: the base name and its qualifiers. */
    public val localePattern: String,
    /** `{0}, {1}`: one qualifier and the next. */
    public val localeSeparator: String,
    /** `{0}: {1}`: a key and its value. */
    public val localeKeyTypePattern: String,
) {

    public companion object {

        /** CLDR root's, which is what a source with nothing for a locale falls back to. */
        public val Root: LocaleDisplayPatterns = LocaleDisplayPatterns("{0} ({1})", "{0}, {1}", "{0}: {1}")
    }
}

/**
 * A source of localized language, script and region names, and of the patterns
 * that compose them into a locale display name.
 *
 * Keyed by subtag rather than by [Locale], for the same reason the country
 * source is keyed by alpha-2 code: the contract should not depend on which
 * locales a particular build kept.
 */
public interface LanguageNameSource : LocaleDataSource {

    /**
     * The name of the language [subtag] names, written in [locale].
     *
     * [subtag] may be a whole CLDR locale id such as `en_GB` or `zh_Hans`, which
     * is how a dialect name is looked up: CLDR declares `British English` under
     * `en_GB` rather than composing it.
     */
    public fun languageNameOrNull(subtag: String, style: LanguageNameStyle, locale: Locale): String?

    /** The name of the ISO 15924 script [code] in [locale], e.g. `Latin` for `Latn`. */
    public fun scriptNameOrNull(code: String, locale: Locale): String?

    /**
     * The name of the region [code] in [locale].
     *
     * Wider than the country domain's: this answers for the macro-regions a
     * locale identifier can carry, so `es-419` reads `Latin America` rather than
     * falling through to the raw code.
     */
    public fun regionNameOrNull(code: String, locale: Locale): String?

    /** The composition patterns for [locale], or `null` when this source has none. */
    public fun displayPatternsOrNull(locale: Locale): LocaleDisplayPatterns?

    /**
     * [name] capitalized the way [locale] capitalizes a name of [usage] shown in
     * [capitalization], which for most locales is [name] unchanged.
     *
     * CLDR stores a language name as the language writes it in running text,
     * which is lower case in many. Whether a picker row shows `Čeština` or
     * `čeština` is a property of the language and CLDR records it, so it is a
     * lookup rather than a call to uppercase the first letter.
     */
    public fun capitalized(name: String, usage: LanguageNameUsage, capitalization: Capitalization, locale: Locale): String = name
}

/** Which kind of name is being capitalized; CLDR records the answer per usage. */
public enum class LanguageNameUsage { LANGUAGE, SCRIPT, TERRITORY }

/** Answers from [primary], and from [fallback] wherever primary has nothing. */
public class FallbackLanguageNames(private val primary: LanguageNameSource, private val fallback: LanguageNameSource) :
    LanguageNameSource {

    override val supportedLocales: Set<Locale> get() = primary.supportedLocales + fallback.supportedLocales

    override fun languageNameOrNull(subtag: String, style: LanguageNameStyle, locale: Locale): String? =
        primary.languageNameOrNull(subtag, style, locale) ?: fallback.languageNameOrNull(subtag, style, locale)

    override fun scriptNameOrNull(code: String, locale: Locale): String? =
        primary.scriptNameOrNull(code, locale) ?: fallback.scriptNameOrNull(code, locale)

    override fun regionNameOrNull(code: String, locale: Locale): String? =
        primary.regionNameOrNull(code, locale) ?: fallback.regionNameOrNull(code, locale)

    override fun displayPatternsOrNull(locale: Locale): LocaleDisplayPatterns? =
        primary.displayPatternsOrNull(locale) ?: fallback.displayPatternsOrNull(locale)

    override fun capitalized(name: String, usage: LanguageNameUsage, capitalization: Capitalization, locale: Locale): String =
        primary.capitalized(name, usage, capitalization, locale)
}
