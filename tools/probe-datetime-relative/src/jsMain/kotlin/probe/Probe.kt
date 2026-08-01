@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.RelativeTimeUnit
import dev.carcara.kotlinx.locale.datetime.cldr.relative.formatRelative

/** Relative wording, without the date patterns. */
@JsExport
public fun probe(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return listOf(
        (-1L).formatRelative(RelativeTimeUnit.DAY, locale = locale),
        (-3L).formatRelative(RelativeTimeUnit.DAY, locale = locale),
    ).joinToString(" ")
}
