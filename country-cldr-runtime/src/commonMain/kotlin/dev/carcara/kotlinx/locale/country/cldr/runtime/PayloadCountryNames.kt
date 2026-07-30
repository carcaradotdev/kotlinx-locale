@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.country.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.CountryNameSource
import dev.carcara.kotlinx.locale.internal.sparseRecordValue
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf

/**
 * A [CountryNameSource] over a table of CLDR name records.
 *
 * The table is a constructor argument rather than something this class knows
 * about, which is the whole point of the module: the shipped `-cldr-full`
 * artifact hands it the full 1121-locale set, and a build that generated a
 * narrowed set hands it that instead. Both get the same lookup, so a narrowed
 * build cannot resolve names differently from a full one.
 *
 * Records are sparse and carry their parent tag, so a lookup walks the CLDR
 * inheritance chain and honors the `parentLocales` overrides: `es-AR` reads its
 * names from `es-419`.
 */
public class PayloadCountryNames(private val records: Map<String, String>) : CountryNameSource {

    override val supportedLocales: Set<Locale> by lazy {
        supportedLocalesOf(records)
    }

    override fun countryNameOrNull(alpha2: String, locale: Locale): String? =
        sparseRecordValue(records, locale, field = 1, fieldCount = 2, key = alpha2)
}
