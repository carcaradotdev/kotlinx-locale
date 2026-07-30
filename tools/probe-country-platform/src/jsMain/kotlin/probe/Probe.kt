@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.country.forAlpha2
import dev.carcara.kotlinx.locale.country.platform.PlatformCountry
import dev.carcara.kotlinx.locale.country.platform.displayName
import dev.carcara.kotlinx.locale.country.platform.forDisplayNameOrNull

/** Codes plus names from the host. Call for call identical to probe-country-full. */
@JsExport
public fun probe(code: String, tag: String, name: String): String {
    val locale = Locale.forLanguageTag(tag)
    return listOf(
        Country.forAlpha2(code).displayName(locale),
        Country.forDisplayNameOrNull(name, locale)?.alpha2,
        PlatformCountry.supportedLocales.size.toString(),
    ).joinToString(" ")
}
