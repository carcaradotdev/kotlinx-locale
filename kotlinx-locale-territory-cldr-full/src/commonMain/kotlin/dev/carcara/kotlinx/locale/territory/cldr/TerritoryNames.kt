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

package dev.carcara.kotlinx.locale.territory.cldr

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.internal.sparseRecordValue
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf
import dev.carcara.kotlinx.locale.territory.cldr.internal.data.countryNamesRegistry

/**
 * The CLDR territory names, keyed by the alpha-2 code CLDR itself keys them by.
 *
 * Two domains ask for these. `Country.displayName` wants the name of a country
 * it holds as an enum entry, and `regionName` wants the name of whatever
 * two-letter code a locale tag carried. Both are the same table, and it used to
 * ship in both artifacts: 54118 entries, byte for byte the same, in every one of
 * the 1121 locales.
 *
 * Here instead, keyed by `String` rather than by `Country`, so that a consumer
 * who only wants region names does not compile the enum to get them.
 */
public object CldrTerritory {

    /** Every locale this build carries names for. */
    public val supportedLocales: Set<Locale> by lazy { supportedLocalesOf(countryNamesRegistry) }

    /**
     * The name of the territory [alpha2] in [locale], or `null`.
     *
     * Resolved through the locale's inheritance chain, so `es-AR` reads what
     * `es-419` declares where it declares nothing itself.
     */
    public fun nameOrNull(alpha2: String, locale: Locale): String? =
        sparseRecordValue(countryNamesRegistry, locale, field = 1, fieldCount = 2, key = alpha2)
}
