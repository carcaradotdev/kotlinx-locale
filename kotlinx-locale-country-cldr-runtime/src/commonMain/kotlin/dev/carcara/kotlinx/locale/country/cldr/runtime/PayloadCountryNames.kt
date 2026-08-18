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

package dev.carcara.kotlinx.locale.country.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.CountryNameSource
import dev.carcara.kotlinx.locale.internal.sparseRecordValue
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf

/**
 * A [CountryNameSource] over a table of CLDR name records.
 *
 * The table is a constructor argument rather than something this class knows
 * about, which is the whole point of the module: the shipped `-cldr-full`
 * artifact hands it the full 1121-locale set, and a build that generated a
 * narrowed set hands it that instead. Both get the same lookup, so a narrowed
 * build cannot resolve names differently from a full one.
 *
 * Records are sparse and carry their parent tag, so a lookup walks the CLDR
 * inheritance chain and honors the `parentLocales` overrides: `es-AR` reads its
 * names from `es-419`.
 */
public class PayloadCountryNames private constructor(private val lookup: (String, Locale) -> String?, supported: () -> Set<Locale>) :
    CountryNameSource {

    /**
     * Over a table of records, which is what a generated build hands it.
     */
    public constructor(records: Map<String, String>) : this(
        { alpha2, locale -> sparseRecordValue(records, locale, field = 1, fieldCount = 2, key = alpha2) },
        { supportedLocalesOf(records) },
    )

    /**
     * Over a lookup somebody else owns, which is what the shipped artifact hands
     * it: the table lives in `kotlinx-locale-territory-cldr-full`, where the
     * language domain can read it too.
     */
    public constructor(lookup: (String, Locale) -> String?, supported: Set<Locale>) : this(lookup, { supported })

    override val supportedLocales: Set<Locale> by lazy(supported)

    override fun countryNameOrNull(alpha2: String, locale: Locale): String? = lookup(alpha2, locale)

    public companion object
}
