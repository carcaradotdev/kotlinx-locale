@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale

/** The floor: parsing a tag and nothing else. */
@JsExport
public fun probe(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return listOf(locale.toLanguageTag(), locale.language, locale.region, Locale.current.toLanguageTag())
        .joinToString(" ")
}
