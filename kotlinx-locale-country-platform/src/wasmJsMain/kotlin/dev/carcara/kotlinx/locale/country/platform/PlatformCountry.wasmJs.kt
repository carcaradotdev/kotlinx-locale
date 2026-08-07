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

/**
 * `Intl.DisplayNames` throws on a malformed tag and returns undefined for an
 * unknown region, so both are folded into null here rather than left to surface
 * as a JS exception in common code.
 */
private fun intlRegionName(localeTag: String, alpha2: String): String? = js(
    "(function(){try{return new Intl.DisplayNames([localeTag],{type:'region',fallback:'none'}).of(alpha2)||null}catch(e){return null}})()",
)

internal actual fun platformCountryName(alpha2: String, localeTag: String): String? = intlRegionName(localeTag, alpha2)
