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

package dev.carcara.kotlinx.locale.timezone

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.conformance.assertConformsToIcuTimeZoneNames
import dev.carcara.kotlinx.locale.country.cldr.CldrCountry
import dev.carcara.kotlinx.locale.number.cldr.CldrNumber
import dev.carcara.kotlinx.locale.timezone.cldr.CldrTimeZone
import dev.carcara.kotlinx.locale.timezone.cldr.cities.internal.data.timeZoneCitiesRegistry
import dev.carcara.kotlinx.locale.timezone.cldr.runtime.PayloadTimeZoneNames
import kotlin.test.Test

/**
 * Held against ICU with every table this domain can be given.
 *
 * Composed by hand rather than using `CldrTimeZoneCities`, because the country
 * names and the number symbols are the two things that object deliberately does
 * not reach for: naming a zone must not drag four hundred kilobytes of country
 * tables into a build that only wanted `Pacific Standard Time`. This is the
 * other end of that choice, where a build did ask for them, and it is the
 * configuration the generic location format is written for.
 */
class CldrTimeZoneConformanceTest {

    private val source = PayloadTimeZoneNames(
        CldrTimeZone.formatRecords,
        CldrTimeZone.nameRecords,
        timeZoneCitiesRegistry,
        CldrTimeZone.metadata,
        CldrNumber,
    ) { region, locale -> CldrCountry.countryNameOrNull(region, locale) }

    @Test
    fun namesAgreeWithIcu() = source.assertConformsToIcuTimeZoneNames()
}
