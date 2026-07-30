package dev.carcara.kotlinx.locale.country.platform

/**
 * `Intl.DisplayNames` throws on a malformed tag and returns undefined for an
 * unknown region, so both are folded into null here rather than left to surface
 * as a JS exception in common code.
 */
private fun intlRegionName(localeTag: String, alpha2: String): String? = js(
    "(function(){try{return new Intl.DisplayNames([localeTag],{type:'region',fallback:'none'}).of(alpha2)||null}catch(e){return null}})()",
)

internal actual fun platformCountryName(alpha2: String, localeTag: String): String? = intlRegionName(localeTag, alpha2)
