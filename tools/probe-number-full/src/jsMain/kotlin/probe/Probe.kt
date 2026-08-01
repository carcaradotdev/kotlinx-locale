@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.NumberNotation
import dev.carcara.kotlinx.locale.number.cldr.numberFormat
import dev.carcara.kotlinx.locale.number.cldr.numberFormatPercent
import dev.carcara.kotlinx.locale.number.cldr.numberOrdinal
import dev.carcara.kotlinx.locale.number.cldr.pluralCategory

/** Numbers, percentages, compact notation, plurals and ordinals. */
@JsExport
public fun probe(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return listOf(
        numberFormat(1234567L, locale),
        numberFormat(1200L, locale, NumberNotation.COMPACT_SHORT),
        numberFormatPercent(Decimal.parse("0.125"), locale, fractionDigits = 1),
        numberOrdinal(1L, locale),
        pluralCategory(3L, locale).name,
    ).joinToString(" ")
}
