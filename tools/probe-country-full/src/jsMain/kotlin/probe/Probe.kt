@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.country.cldr.CldrCountry
import dev.carcara.kotlinx.locale.country.cldr.displayName
import dev.carcara.kotlinx.locale.country.cldr.forDisplayNameOrNull
import dev.carcara.kotlinx.locale.country.forAlpha2

/** Codes plus the CLDR name tables. */
@JsExport
public fun probe(code: String, tag: String, name: String): String {
    val locale = Locale.forLanguageTag(tag)
    return listOf(
        Country.forAlpha2(code).displayName(locale),
        Country.forDisplayNameOrNull(name, locale)?.alpha2,
        CldrCountry.supportedLocales.size.toString(),
    ).joinToString(" ")
}
