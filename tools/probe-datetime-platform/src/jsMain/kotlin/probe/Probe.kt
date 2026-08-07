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
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.TextStyle
import dev.carcara.kotlinx.locale.datetime.platform.displayName
import dev.carcara.kotlinx.locale.datetime.platform.format
import kotlinx.datetime.LocalDateTime

/** The full datetime surface over the host. Call for call probe-datetime-full. */
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
