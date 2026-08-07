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
 * `Intl.DisplayNames` as an external declaration rather than a `js("...")`
 * string.
 *
 * The string form reads more directly but cannot see this function's parameters
 * on Kotlin/JS: the names it would reference are not the names the compiler
 * emits, so the lookup quietly receives undefined and every name comes back
 * null. Kotlin/Wasm does support that form, which is why the two actuals differ.
 */
@JsName("Intl")
private external object Intl {
    class DisplayNames(locales: Array<String>, options: dynamic) {
        fun of(code: String): String?
    }
}

internal actual fun platformCountryName(alpha2: String, localeTag: String): String? = try {
    val options: dynamic = js("({ type: 'region', fallback: 'none' })")
    // Throws on a malformed tag and returns undefined for a region it does not
    // know; both are a miss rather than an error worth propagating.
    Intl.DisplayNames(arrayOf(localeTag), options).of(alpha2)
} catch (_: Throwable) {
    null
}
