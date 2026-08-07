package dev.carcara.kotlinx.locale

/**
 * A compile-time name for one locale, as opposed to a string that is only
 * checked when it is parsed.
 *
 * `kotlinx-locale-types` generates one implementation per language, so
 * `PT.BR.tag` cannot be misspelled and autocompletes, and `PT` alone is the bare
 * `pt`. That matters most in the Gradle plugin, whose configuration is a locale
 * set: a typo there does not throw, it quietly generates data for one locale
 * fewer than intended.
 *
 * Nothing requires it in application code. [Locale.forLanguageTag] stays the
 * zero-cost path for tags built at runtime.
 */
public interface LocaleRef {

    /** The canonical BCP 47 tag, e.g. `pt-BR` or `zh-Hans-CN`. */
    public val tag: String

    public companion object
}

/** The locale this reference names. */
public fun LocaleRef.toLocale(): Locale = Locale.forLanguageTag(tag)
