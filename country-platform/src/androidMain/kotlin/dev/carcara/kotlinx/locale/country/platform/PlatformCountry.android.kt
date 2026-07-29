package dev.carcara.kotlinx.locale.country.platform

internal actual fun platformCountryName(alpha2: String, localeTag: String): String? {
    val displayIn = java.util.Locale.forLanguageTag(localeTag)
    // A region-only locale is what getDisplayCountry reads; building it through
    // the builder rather than the deprecated constructor keeps it on the
    // supported path.
    val country = java.util.Locale.Builder().setRegion(alpha2).build()
    return country.getDisplayCountry(displayIn).takeIf(String::isNotEmpty)
}
