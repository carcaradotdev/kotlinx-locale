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

package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.forLocaleOrNull
import dev.carcara.kotlinx.locale.currency.internal.primaryCurrencyOf
import dev.carcara.kotlinx.locale.number.internal.rescaleFraction

private val byCode: Map<String, Currency> by lazy { Currency.entries.associateBy(Currency::name) }

/** The ISO 4217 alphabetic code, e.g. `USD`. */
public val Currency.code: String
    get() = name

/**
 * The fraction digits of ISO minor-unit amounts such as
 * [CurrencyAmount.minorUnits]: [Currency.defaultFractionDigits], or 0 when ISO
 * defines no minor units.
 */
public val Currency.minorUnitDigits: Int
    get() = if (defaultFractionDigits >= 0) defaultFractionDigits else 0

/**
 * Converts an amount in ISO minor units to the CLDR fraction scale, rounding
 * half-even when CLDR uses fewer digits than ISO.
 * For ALL (ISO 2 decimals, CLDR 0): `12345 -> 123`.
 */
@OptIn(InternalKotlinxLocaleApi::class)
public fun Currency.isoToCldrUnits(minorUnits: Long): Long = rescaleFraction(minorUnits, minorUnitDigits, cldrFractionDigits)

/**
 * Converts an amount in the CLDR fraction scale back to ISO minor units.
 * For ALL: `123 -> 12300`.
 */
@OptIn(InternalKotlinxLocaleApi::class)
public fun Currency.cldrToIsoUnits(cldrUnits: Long): Long = rescaleFraction(cldrUnits, cldrFractionDigits, minorUnitDigits)

/** The currency with the given ISO 4217 alphabetic code, case-insensitively, or `null`. */
public fun Currency.Companion.forCodeOrNull(code: String): Currency? = byCode[code.uppercase()]

/** Like [forCodeOrNull] but throws on unknown codes. */
public fun Currency.Companion.forCode(code: String): Currency = requireNotNull(forCodeOrNull(code)) { "Unknown ISO 4217 code: '$code'" }

/** The primary legal-tender currency of [country] per CLDR, or `null`. */
public fun Currency.Companion.forCountryOrNull(country: Country): Currency? = primaryCurrencyOf(country)

/** The primary currency of [locale]'s region, or `null`. */
public fun Currency.Companion.forLocaleOrNull(locale: Locale = Locale.current): Currency? =
    Country.forLocaleOrNull(locale)?.let { forCountryOrNull(it) }
