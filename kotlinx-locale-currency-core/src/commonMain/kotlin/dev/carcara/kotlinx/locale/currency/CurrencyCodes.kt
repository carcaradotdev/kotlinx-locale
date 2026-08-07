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
import dev.carcara.kotlinx.locale.currency.internal.currencyTenderTable
import dev.carcara.kotlinx.locale.currency.internal.primaryCurrencyOf
import dev.carcara.kotlinx.locale.number.internal.rescaleFraction

private val byCode: Map<String, Currency> by lazy { Currency.entries.associateBy(Currency::name) }

/**
 * ISO reuses a numeric code across generations of the same currency: 191 is both
 * the 1991 Croatian dinar and the kuna that replaced it. So the active entry
 * owns the number, and `associateBy` cannot be used here because it silently
 * keeps whichever came last.
 */
private val byNumeric: Map<Int, Currency> by lazy {
    val result = HashMap<Int, Currency>()
    for (currency in Currency.entries) {
        if (currency.numericCode < 0) continue
        val existing = result[currency.numericCode]
        if (existing == null || (!existing.isActive && currency.isActive)) result[currency.numericCode] = currency
    }
    result
}

/** `from`, `to` and the tender flag per ordinal, decoded once. */
private val tenderWindows: IntArray by lazy {
    val rows = currencyTenderTable.split(';')
    val decoded = IntArray(rows.size * 4)
    for ((index, row) in rows.withIndex()) {
        val fields = row.split(',')
        decoded[index * 4] = fields[0].toInt()
        decoded[index * 4 + 1] = fields[1].toInt()
        decoded[index * 4 + 2] = fields[2].toInt()
        decoded[index * 4 + 3] = fields[3].toInt()
    }
    decoded
}

/**
 * True while ISO still lists this code.
 *
 * List membership rather than the CLDR tender window, because the two disagree
 * and ISO owns this question: CLDR closed the Salvadoran colón when El Salvador
 * adopted the dollar, and ISO still publishes SVC. The window says when a code
 * was legal tender somewhere; this says whether the standard still carries it.
 *
 * This is the filter a currency picker wants. [Currency.entries] carries the
 * withdrawn codes as well, because an amount denominated in one still has to
 * render.
 */
public val Currency.isActive: Boolean
    get() = tenderWindows[ordinal * 4 + 3] == 1

/**
 * True unless ISO marks this as a non-tender fund or metal code.
 *
 * A different axis from withdrawal: `XXX` has never been withdrawn and has never
 * been tender.
 */
public val Currency.isTender: Boolean
    get() = tenderWindows[ordinal * 4 + 2] == 1

/**
 * The first day any region made this legal tender, as a proleptic Gregorian day
 * number; [Int.MIN_VALUE] when CLDR records no start.
 *
 * A day number rather than a `LocalDate` because the currency domain must not
 * take a dependency on a date library to answer a question about codes.
 * `LocalDate.fromEpochDays(currency.firstTenderEpochDay)` is one line on the
 * calling side.
 */
public val Currency.firstTenderEpochDay: Int
    get() = tenderWindows[ordinal * 4]

/** The last day any region held this as legal tender; [Int.MAX_VALUE] while it is still current. */
public val Currency.lastTenderEpochDay: Int
    get() = tenderWindows[ordinal * 4 + 1]

/** Whether any region held this as legal tender on [epochDay]. ICU's `Currency.isAvailable`. */
public fun Currency.wasTenderOn(epochDay: Int): Boolean = epochDay >= firstTenderEpochDay && epochDay <= lastTenderEpochDay

/**
 * The currencies still in use, which is what a picker should offer.
 *
 * [Currency.entries] is both kinds, so code that used it to mean "codes we
 * accept" wants this instead.
 */
public val Currency.Companion.active: List<Currency>
    get() = Currency.entries.filter(Currency::isActive)

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

/** The currency with the given ISO 4217 numeric code, or `null`. */
public fun Currency.Companion.forNumericCodeOrNull(code: Int): Currency? = byNumeric[code]

/** Like [forNumericCodeOrNull] but throws on unknown codes. */
public fun Currency.Companion.forNumericCode(code: Int): Currency =
    requireNotNull(forNumericCodeOrNull(code)) { "Unknown ISO 4217 numeric code: $code" }

/** The primary legal-tender currency of [country] per CLDR, or `null`. */
public fun Currency.Companion.forCountryOrNull(country: Country): Currency? = primaryCurrencyOf(country)

/** The primary currency of [locale]'s region, or `null`. */
public fun Currency.Companion.forLocaleOrNull(locale: Locale = Locale.current): Currency? =
    Country.forLocaleOrNull(locale)?.let { forCountryOrNull(it) }
