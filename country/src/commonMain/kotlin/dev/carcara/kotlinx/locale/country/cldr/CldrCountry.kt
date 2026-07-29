package dev.carcara.kotlinx.locale.country.cldr

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.CountryNameSource
import dev.carcara.kotlinx.locale.country.cldr.internal.bundledCountryName
import dev.carcara.kotlinx.locale.country.cldr.internal.bundledCountryNameLocales

/**
 * The country names CLDR ships, compiled into this artifact.
 *
 * Names are resolved through the locale's CLDR inheritance chain, honoring the
 * `parentLocales` overrides, so `es-AR` reads its names from `es-419`.
 */
public object CldrCountry : CountryNameSource {

    override val supportedLocales: Set<Locale>
        get() = bundledCountryNameLocales

    override fun countryNameOrNull(alpha2: String, locale: Locale): String? = bundledCountryName(alpha2, locale)
}
