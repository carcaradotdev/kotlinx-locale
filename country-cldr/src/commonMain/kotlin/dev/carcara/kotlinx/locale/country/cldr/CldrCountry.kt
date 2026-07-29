package dev.carcara.kotlinx.locale.country.cldr

import dev.carcara.kotlinx.locale.country.CountryNameSource
import dev.carcara.kotlinx.locale.country.cldr.format.PayloadCountryNames
import dev.carcara.kotlinx.locale.country.cldr.internal.data.countryNamesRegistry

/**
 * The country names CLDR ships, compiled into this artifact.
 *
 * Names resolve through the locale's CLDR inheritance chain, honoring the
 * `parentLocales` overrides, so `es-AR` reads its names from `es-419`.
 *
 * All this object contributes is the table. The lookup lives in
 * `kotlinx-locale-country-cldr-format`, which is also what a build that
 * generated a narrowed table binds to, so there is one implementation of the
 * record format rather than one per data set.
 */
public object CldrCountry : CountryNameSource by PayloadCountryNames(countryNamesRegistry)
