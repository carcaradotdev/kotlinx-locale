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

package dev.carcara.kotlinx.locale.country.platform

internal actual fun platformCountryName(alpha2: String, localeTag: String): String? {
    val displayIn = java.util.Locale.forLanguageTag(localeTag)
    // A region-only locale is what getDisplayCountry reads; building it through
    // the builder rather than the deprecated constructor keeps it on the
    // supported path.
    val country = java.util.Locale.Builder().setRegion(alpha2).build()
    return country.getDisplayCountry(displayIn).takeIf(String::isNotEmpty)
}
