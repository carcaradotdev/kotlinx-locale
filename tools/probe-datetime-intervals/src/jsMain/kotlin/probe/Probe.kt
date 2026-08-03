@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.intervals.intervalFormat
import kotlinx.datetime.LocalDate

/** A range that collapses its shared month, and one that shares only the year. */
@JsExport
public fun probe(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return intervalFormat(LocalDate(2026, 7, 18), LocalDate(2026, 7, 22), "yMMMd", locale) +
        " " + intervalFormat(LocalDate(2026, 5, 18), LocalDate(2026, 7, 22), "yMMMd", locale)
}
