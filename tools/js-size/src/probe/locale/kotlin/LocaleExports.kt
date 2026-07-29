@file:OptIn(InternalKotlinxLocaleApi::class)

package probe

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.dataLookupTags

/**
 * Touches every public declaration of `dev.carcara:kotlinx-locale`.
 *
 * All inputs arrive as parameters and every result feeds the return value, so
 * neither Kotlin's DCE nor webpack can fold a call away and drop the code behind it.
 */
@JsExport
fun localeSurface(tag: String, language: String, script: String, region: String, variant: String): String {
    val parsed = Locale.forLanguageTag(tag)
    val parsedOrNull = Locale.forLanguageTagOrNull(tag)
    val assembled = Locale.of(language, script, region, variant)
    val defaulted = Locale.of(language)

    return buildString {
        append(parsed.language)
        append(parsed.script)
        append(parsed.region)
        append(parsed.variant)
        append(parsed.toLanguageTag())
        append(parsed.toString())
        append(parsed.hashCode())
        append(parsed == parsedOrNull)
        append(parsed.dataLookupTags().joinToString(","))
        append(assembled.toLanguageTag())
        append(defaulted.toLanguageTag())
        append(Locale.current.toLanguageTag())
        append(Locale.availableLocales.size)
    }
}
