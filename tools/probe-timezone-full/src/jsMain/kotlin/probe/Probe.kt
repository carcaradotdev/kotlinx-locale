@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.timezone.TimeZoneNameStyle
import dev.carcara.kotlinx.locale.timezone.cldr.displayName
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset

/** Zone and metazone names, and the localized GMT format. */
@JsExport
public fun probe(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    val zone = TimeZone.of("America/Los_Angeles")
    return listOf(
        zone.displayName(TimeZoneNameStyle.STANDARD_LONG, locale = locale),
        UtcOffset(hours = -8).displayName(locale),
    ).joinToString(" ")
}
