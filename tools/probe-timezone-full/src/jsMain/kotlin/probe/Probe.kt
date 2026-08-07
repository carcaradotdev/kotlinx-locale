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
