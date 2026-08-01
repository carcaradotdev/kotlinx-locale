@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.RelativeTimeUnit
import dev.carcara.kotlinx.locale.datetime.cldr.relative.relativeTimeFormat

/** Relative wording, without the date patterns. */
@JsExport
public fun probe(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return listOf(
        relativeTimeFormat(-1L, RelativeTimeUnit.DAY, locale = locale),
        relativeTimeFormat(-3L, RelativeTimeUnit.DAY, locale = locale),
    ).joinToString(" ")
}
