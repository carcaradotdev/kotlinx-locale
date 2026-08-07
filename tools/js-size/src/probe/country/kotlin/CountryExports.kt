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

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country

/**
 * Touches every public declaration of `dev.carcara:kotlinx-locale-country`,
 * including `displayName` over the full enum, which is what pulls in the CLDR
 * country-name tables.
 */
@JsExport
fun countrySurface(tag: String, alpha2: String, alpha3: String, numericCode: Int, displayName: String): String {
    val locale = Locale.forLanguageTag(tag)

    return buildString {
        for (country in Country.entries) {
            append(country.name)
            append(country.alpha2)
            append(country.alpha3)
            append(country.numericCode)
            append(country.displayName(locale))
            append(country.displayName())
            append(country.ordinal)
        }
        append(Country.valueOf(alpha2).alpha3)
        append(Country.forAlpha2(alpha2).alpha3)
        append(Country.forAlpha2OrNull(alpha2)?.alpha3)
        append(Country.forAlpha3(alpha3).alpha2)
        append(Country.forAlpha3OrNull(alpha3)?.alpha2)
        append(Country.forNumericCode(numericCode).alpha2)
        append(Country.forNumericCodeOrNull(numericCode)?.alpha2)
        append(Country.forLocaleOrNull(locale)?.alpha2)
        append(Country.forLocaleOrNull()?.alpha2)
        append(Country.forDisplayNameOrNull(displayName, locale)?.alpha2)
        append(Country.forDisplayNameOrNull(displayName)?.alpha2)
    }
}
