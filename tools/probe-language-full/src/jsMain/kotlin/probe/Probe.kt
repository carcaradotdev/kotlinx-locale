@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.language.cldr.displayName
import dev.carcara.kotlinx.locale.language.cldr.nativeDisplayName

/** Language, script and region names. */
@JsExport
public fun probe(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return listOf(locale.displayName(Locale.of("en")), locale.nativeDisplayName).joinToString(" ")
}
