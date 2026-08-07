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

package dev.carcara.kotlinx.locale.country

import dev.carcara.kotlinx.locale.Locale

private val byAlpha2: Map<String, Country> by lazy { Country.entries.associateBy(Country::name) }
private val byAlpha3: Map<String, Country> by lazy { Country.entries.associateBy(Country::alpha3) }
private val byNumeric: Map<Int, Country> by lazy { Country.entries.associateBy(Country::numericCode) }

/** The ISO 3166-1 alpha-2 code, e.g. `US`. */
public val Country.alpha2: String
    get() = name

/** The country with the given ISO 3166-1 alpha-2 code, case-insensitively, or `null`. */
public fun Country.Companion.forAlpha2OrNull(code: String): Country? = byAlpha2[code.uppercase()]

/** Like [forAlpha2OrNull] but throws on unknown codes. */
public fun Country.Companion.forAlpha2(code: String): Country =
    requireNotNull(forAlpha2OrNull(code)) { "Unknown ISO 3166-1 alpha-2 code: '$code'" }

/** The country with the given ISO 3166-1 alpha-3 code, case-insensitively, or `null`. */
public fun Country.Companion.forAlpha3OrNull(code: String): Country? = byAlpha3[code.uppercase()]

/** Like [forAlpha3OrNull] but throws on unknown codes. */
public fun Country.Companion.forAlpha3(code: String): Country =
    requireNotNull(forAlpha3OrNull(code)) { "Unknown ISO 3166-1 alpha-3 code: '$code'" }

/** The country with the given ISO 3166-1 numeric code, or `null`. */
public fun Country.Companion.forNumericCodeOrNull(code: Int): Country? = byNumeric[code]

/** Like [forNumericCodeOrNull] but throws on unknown codes. */
public fun Country.Companion.forNumericCode(code: Int): Country =
    requireNotNull(forNumericCodeOrNull(code)) { "Unknown ISO 3166-1 numeric code: $code" }

/** The country of [locale]'s region subtag, or `null`. Needs no locale data. */
public fun Country.Companion.forLocaleOrNull(locale: Locale = Locale.current): Country? = locale.region?.let { byAlpha2[it] }
