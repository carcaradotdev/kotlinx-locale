package dev.carcara.kotlinx.locale.country.cldr

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.countryForDisplayNameOrNull
import dev.carcara.kotlinx.locale.country.displayName

/**
 * The country name for [locale] from CLDR data, resolved through the locale's
 * inheritance chain; falls back to the alpha-2 code when CLDR has no name.
 */
public fun Country.displayName(locale: Locale = Locale.current): String = CldrCountry.displayName(this, locale)

/**
 * The country whose CLDR display name in [locale] matches [name],
 * case-insensitively, or `null`.
 */
public fun Country.Companion.forDisplayNameOrNull(name: String, locale: Locale = Locale.current): Country? =
    CldrCountry.countryForDisplayNameOrNull(name, locale)
