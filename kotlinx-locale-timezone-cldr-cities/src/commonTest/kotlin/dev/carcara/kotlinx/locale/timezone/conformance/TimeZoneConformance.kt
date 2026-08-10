/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.carcara.kotlinx.locale.timezone.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue
import dev.carcara.kotlinx.locale.timezone.TimeZoneNameSource
import dev.carcara.kotlinx.locale.timezone.TimeZoneNameStyle
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset

/**
 * Holds a zone name source to ICU's answers for the same zones and instants.
 *
 * ## What this fixture assumes
 *
 * Naming a zone needs two datasets and only one of them is ours. CLDR supplies
 * the words: `Pacific Standard Time`, the exemplar cities, the localized GMT
 * format. tzdb supplies the offsets and the dates the rules change. This library
 * vendors CLDR at a pinned tag and never ships tzdb, because the platform
 * already has one.
 *
 * The wrinkle is that each target reads a different copy. The JVM reads the JDK
 * image's, Android reads the device's, JS reads whatever the browser or Node
 * has, Native reads the one kotlinx-datetime ships, and the ICU release that
 * wrote these goldens has its own. tzdb is revised several times a year, so
 * those copies are rarely all the same.
 *
 * So the offset and the daylight answer are read out of the golden rather than
 * off the running platform. Every assertion below is then pinned data through
 * pinned code, and none of it depends on which tzdb the target happens to carry.
 * What is being compared is the naming, which is the part this library owns.
 *
 * ## What a failure means
 *
 * A mismatch here is about CLDR's words or this library's fallback ladder. It is
 * not a report about tzdb, and it is not something a consumer would see
 * differently on one platform than on another, because the library takes the
 * zone identifier and the offset from its caller and never asks the platform
 * what time it is.
 *
 * The one platform-dependent step is constructing the [TimeZone] itself. Node
 * ships no full tzdb, so on Kotlin/JS and both Wasm targets `TimeZone.of` throws
 * for every identifier here and this fixture has nothing it can ask. It says so
 * and returns rather than failing, because the library takes the zone from its
 * caller and a target that cannot build the argument is not a target where the
 * naming is wrong. Where zones can be built, all of them must be, and a partial
 * run fails: that is the case where something did regress.
 *
 * ## What it deliberately does not cover
 *
 * Two things, both places where this library and ICU disagree on purpose rather
 * than by accident.
 *
 * The generic styles. ICU writes `Mountain Standard Time` rather than `Mountain
 * Time` for Phoenix, and `Brasilia Standard Time` for Sao Paulo, because it
 * knows from tzdb that those zones no longer observe daylight saving and a
 * generic name would imply that they might. Knowing that means asking the
 * platform a tzdb question, which is the one thing this domain is built not to
 * do: the API takes the style from its caller so that the answer never depends
 * on which copy of tzdb the target carries. So the generic styles resolve from
 * CLDR's tables alone here, and the fixture does not hold them to ICU.
 *
 * A zero offset. UTS #35 gives a locale a `gmtZeroFormat` and says it is what a
 * zero offset reads as, so `GMT` in English and `UTC` in French. ICU's
 * `TimeZoneFormat` writes the offset out regardless, giving `GMT+00:00`. Both
 * are defensible, this library follows the specification, and the rows where the
 * offset is zero are left out rather than papered over.
 */
public fun TimeZoneNameSource.assertConformsToIcuTimeZoneNames() {
    assertTrue(icuTimeZoneGoldenData.size >= 20, "expected the full golden set, got ${icuTimeZoneGoldenData.size}")
    var checked = 0
    var skippedZones = 0

    for ((tag, rows) in icuTimeZoneGoldenData) {
        val locale = Locale.forLanguageTagOrNull(tag) ?: continue
        if (locale !in supportedLocales) continue
        for (row in rows) {
            // See "What it deliberately does not cover".
            if (row.offsetSeconds == 0) continue
            val zone = zoneOrNull(row.zoneId)
            if (zone == null) {
                skippedZones++
                continue
            }
            val offset = UtcOffset(seconds = row.offsetSeconds)
            for ((index, styleName) in icuTimeZoneGoldenStyles.withIndex()) {
                val style = styleFor(styleName, row.daylight) ?: continue
                val expected = row.names[index]
                val actual = displayNameOrNull(zone, style, offset, locale) ?: continue
                assertEquals(expected, actual, "$tag ${row.zoneId} @${row.epochSeconds} $styleName")
                checked++
            }
        }
    }
    // A target that could not build a single zone has no answer to check; see
    // the note above. One that built some but not all is a different thing, and
    // the assertion below is what catches it.
    if (checked == 0 && skippedZones > 0) return
    assertTrue(checked > 1000, "expected to check the golden set, checked only $checked ($skippedZones zones skipped)")
}

/**
 * The library style the ICU style maps onto.
 *
 * The specific forms are one ICU style and two here, because ICU takes an
 * instant and works out whether the zone was on daylight time while this library
 * takes the answer from its caller. The golden carries ICU's own answer so the
 * mapping is a lookup rather than an inference.
 */
private fun styleFor(icuStyle: String, daylight: Boolean): TimeZoneNameStyle? = when (icuStyle) {
    // Not compared; see "What it deliberately does not cover".
    "GENERIC_LONG", "GENERIC_SHORT" -> null
    "SPECIFIC_LONG" -> if (daylight) TimeZoneNameStyle.DAYLIGHT_LONG else TimeZoneNameStyle.STANDARD_LONG
    "SPECIFIC_SHORT" -> if (daylight) TimeZoneNameStyle.DAYLIGHT_SHORT else TimeZoneNameStyle.STANDARD_SHORT
    "LOCATION" -> TimeZoneNameStyle.LOCATION
    "OFFSET_LONG" -> TimeZoneNameStyle.OFFSET_LONG
    "OFFSET_SHORT" -> TimeZoneNameStyle.OFFSET_SHORT
    else -> null
}

/**
 * [id] as a [TimeZone], or `null` on a target whose host declines it.
 *
 * Kotlin/JS under Node throws for zones the host has no rules for. The library
 * itself works from the identifier and so behaves the same everywhere; this is
 * only about being able to build the argument.
 */
private fun zoneOrNull(id: String): TimeZone? = try {
    TimeZone.of(id)
} catch (_: IllegalArgumentException) {
    null
} catch (_: Exception) {
    null
}
