@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.durations.durationFormat
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.DurationUnit
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.UnitWidth

/** Duration wording, without the date patterns. */
@JsExport
public fun probe(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return listOf(
        durationFormat(2L, DurationUnit.HOUR, UnitWidth.LONG, locale),
        durationFormat(90L, DurationUnit.MINUTE, UnitWidth.NARROW, locale),
    ).joinToString(" ")
}
