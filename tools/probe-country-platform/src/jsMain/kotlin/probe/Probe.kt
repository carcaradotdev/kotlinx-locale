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
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.country.forAlpha2
import dev.carcara.kotlinx.locale.country.platform.PlatformCountry
import dev.carcara.kotlinx.locale.country.platform.displayName
import dev.carcara.kotlinx.locale.country.platform.forDisplayNameOrNull

/** Codes plus names from the host. Call for call identical to probe-country-full. */
@JsExport
public fun probe(code: String, tag: String, name: String): String {
    val locale = Locale.forLanguageTag(tag)
    return listOf(
        Country.forAlpha2(code).displayName(locale),
        Country.forDisplayNameOrNull(name, locale)?.alpha2,
        PlatformCountry.supportedLocales.size.toString(),
    ).joinToString(" ")
}
