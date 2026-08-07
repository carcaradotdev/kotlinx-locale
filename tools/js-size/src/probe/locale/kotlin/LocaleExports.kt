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

@file:OptIn(InternalKotlinxLocaleApi::class)

package probe

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.dataLookupTags

/**
 * Touches every public declaration of `dev.carcara:kotlinx-locale`.
 *
 * All inputs arrive as parameters and every result feeds the return value, so
 * neither Kotlin's DCE nor webpack can fold a call away and drop the code behind it.
 */
@JsExport
fun localeSurface(tag: String, language: String, script: String, region: String, variant: String): String {
    val parsed = Locale.forLanguageTag(tag)
    val parsedOrNull = Locale.forLanguageTagOrNull(tag)
    val assembled = Locale.of(language, script, region, variant)
    val defaulted = Locale.of(language)

    return buildString {
        append(parsed.language)
        append(parsed.script)
        append(parsed.region)
        append(parsed.variant)
        append(parsed.toLanguageTag())
        append(parsed.toString())
        append(parsed.hashCode())
        append(parsed == parsedOrNull)
        append(parsed.dataLookupTags().joinToString(","))
        append(assembled.toLanguageTag())
        append(defaulted.toLanguageTag())
        append(Locale.current.toLanguageTag())
        append(Locale.availableLocales.size)
    }
}
