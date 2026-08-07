package dev.carcara.kotlinx.locale.country

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource

/**
 * A source of localized country names.
 *
 * Keyed by ISO 3166-1 alpha-2 code rather than by [Country] so that the
 * contract does not depend on which entry set is in play: an implementation
 * compiled against the full enum still satisfies a build whose enum was
 * narrowed to a handful of countries.
 */
public interface CountryNameSource : LocaleDataSource {

    /**
     * The name of the country with this alpha-2 code in [locale], or `null`
     * when the source has no name for it.
     */
    public fun countryNameOrNull(alpha2: String, locale: Locale): String?

    public companion object
}

/**
 * The name of [country] in [locale]; falls back to the alpha-2 code when the
 * source has no name, which is what CLDR root already does.
 */
public fun CountryNameSource.displayName(country: Country, locale: Locale): String =
    countryNameOrNull(country.alpha2, locale) ?: country.alpha2

/**
 * The country whose name in [locale] matches [name], case-insensitively and
 * ignoring surrounding whitespace, or `null`.
 *
 * Some locales give two countries the same name, so this returns a country
 * carrying exactly the requested name rather than necessarily the one that
 * produced it.
 */
public fun CountryNameSource.countryForDisplayNameOrNull(name: String, locale: Locale): Country? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    return Country.entries.firstOrNull { displayName(it, locale).equals(trimmed, ignoreCase = true) }
}

/**
 * Answers from [primary], and from [fallback] wherever primary has nothing.
 *
 * The dispatch is per lookup rather than per locale, so a primary that knows a
 * locale but not one country within it still falls through for that country.
 * That is usually what you want, and it does mean one answer can mix sources.
 * [supportedLocales] is the union of both; a caller that needs a single source
 * to answer for a whole locale should consult it before dispatching.
 */
public class FallbackCountryNames(private val primary: CountryNameSource, private val fallback: CountryNameSource) : CountryNameSource {

    override val supportedLocales: Set<Locale>
        get() = primary.supportedLocales + fallback.supportedLocales

    override fun countryNameOrNull(alpha2: String, locale: Locale): String? =
        primary.countryNameOrNull(alpha2, locale) ?: fallback.countryNameOrNull(alpha2, locale)

    public companion object
}
