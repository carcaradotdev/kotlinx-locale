package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country

/**
 * Touches every public declaration of `dev.carcara:kotlinx-locale-country`,
 * including `displayName` over the full enum, which is what pulls in the CLDR
 * country-name tables.
 */
@JsExport
fun countrySurface(tag: String, alpha2: String, alpha3: String, numericCode: Int, displayName: String): String {
    val locale = Locale.forLanguageTag(tag)

    return buildString {
        for (country in Country.entries) {
            append(country.name)
            append(country.alpha2)
            append(country.alpha3)
            append(country.numericCode)
            append(country.displayName(locale))
            append(country.displayName())
            append(country.ordinal)
        }
        append(Country.valueOf(alpha2).alpha3)
        append(Country.forAlpha2(alpha2).alpha3)
        append(Country.forAlpha2OrNull(alpha2)?.alpha3)
        append(Country.forAlpha3(alpha3).alpha2)
        append(Country.forAlpha3OrNull(alpha3)?.alpha2)
        append(Country.forNumericCode(numericCode).alpha2)
        append(Country.forNumericCodeOrNull(numericCode)?.alpha2)
        append(Country.forLocaleOrNull(locale)?.alpha2)
        append(Country.forLocaleOrNull()?.alpha2)
        append(Country.forDisplayNameOrNull(displayName, locale)?.alpha2)
        append(Country.forDisplayNameOrNull(displayName)?.alpha2)
    }
}
