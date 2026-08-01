@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.timezone.cldr.cities.exemplarCity
import kotlinx.datetime.TimeZone

/** The zone names plus the exemplar cities. */
@JsExport
public fun probe(tag: String): String = TimeZone.of("Europe/Prague").exemplarCity(Locale.forLanguageTag(tag))
