@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.TextStyle
import dev.carcara.kotlinx.locale.datetime.cldr.displayName
import dev.carcara.kotlinx.locale.datetime.cldr.format
import kotlinx.datetime.LocalDateTime

/** The full datetime surface: patterns, month and weekday names. */
@JsExport
public fun probe(iso: String, tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    val moment = LocalDateTime.parse(iso)
    return listOf(
        moment.format(FormatStyle.FULL, locale),
        moment.date.format(FormatStyle.SHORT, locale),
        moment.time.format(FormatStyle.MEDIUM, locale),
        moment.month.displayName(TextStyle.FULL, locale),
        moment.date.dayOfWeek.displayName(TextStyle.NARROW, locale),
    ).joinToString(" ")
}
