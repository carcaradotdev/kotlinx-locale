@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.NumberNotation
import dev.carcara.kotlinx.locale.number.cldr.format
import dev.carcara.kotlinx.locale.number.cldr.formatOrdinal
import dev.carcara.kotlinx.locale.number.cldr.formatPercent
import dev.carcara.kotlinx.locale.number.cldr.pluralCategory

/** Numbers, percentages, compact notation, plurals and ordinals. */
@JsExport
public fun probe(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return listOf(
        1234567L.format(locale),
        1200L.format(locale, NumberNotation.COMPACT_SHORT),
        Decimal.parse("0.125").formatPercent(locale, fractionDigits = 1),
        1L.formatOrdinal(locale),
        3L.pluralCategory(locale).name,
    ).joinToString(" ")
}
