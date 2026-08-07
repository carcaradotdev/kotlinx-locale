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

package dev.carcara.kotlinx.locale.currency.platform

// This target's platform exposes no locale data Kotlin can read, so every lookup
// misses and a consumer composes with a bundled source.

internal actual fun platformCurrencySymbol(currencyCode: String, localeTag: String): String? = null

internal actual fun platformCurrencyName(currencyCode: String, localeTag: String): String? = null

internal actual fun platformFormatCurrency(
    amount: String,
    currencyCode: String,
    localeTag: String,
    useIsoCode: Boolean,
    accounting: Boolean,
): String? = null

internal actual fun platformParseCurrency(text: String, currencyCode: String, localeTag: String): String? = null
