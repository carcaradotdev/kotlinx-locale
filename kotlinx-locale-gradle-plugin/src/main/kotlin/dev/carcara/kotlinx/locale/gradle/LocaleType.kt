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

package dev.carcara.kotlinx.locale.gradle

import dev.carcara.kotlinx.locale.codegen.GeneratedTable

/**
 * One public type a build can generate instead of taking whole from a published
 * artifact.
 *
 * A [LocaleFeature] narrows locale data: which languages this build can answer
 * in. A type narrows an entry set: which locales, countries or currencies it can
 * name at all. The two are independent, which is why they are separate enums
 * rather than more flags on the same one. An app that ships one language may
 * still list every country, and an app that ships forty may only ever name three
 * currencies.
 *
 * [replaces] is the published artifact whose contents this generates. Null for
 * the catalog, which nothing in the library depends on, so a build that
 * generates its own simply does not declare `kotlinx-locale-types`. The other
 * two are `api` dependencies of their `-core` module and arrive whether or not
 * they were asked for, so generating one means the shipped one has to be
 * excluded: `kotlinx-locale-country-core` declares `Country.alpha2` and
 * `Country.Companion.forAlpha2` on `dev.carcara.kotlinx.locale.country.Country`,
 * so a generated enum has to take that exact name and two of them on one
 * classpath is a duplicate class rather than a choice.
 *
 * That is also why the catalog is the only one whose package moves. See
 * `RegistryPackages.catalog`.
 */
enum class LocaleType(val dslName: String, val tables: Set<GeneratedTable>, val replaces: String?) {

    /**
     * The locale catalog: one enum per language, so `PT.BR` is a locale the
     * compiler checks.
     *
     * Takes no entry set of its own. Its entries are the locales the build
     * already declared, which is the whole point: a catalog naming a locale
     * there is no data for is a call that compiles and then answers in the
     * fallback.
     */
    LOCALE_CATALOG(
        dslName = "catalog",
        tables = setOf(GeneratedTable.LOCALE_CATALOG),
        replaces = null,
    ),

    /** The `Country` enum, narrowed to the codes `country { entries(...) }` names. */
    COUNTRY_ENTRIES(
        dslName = "country.entries",
        tables = setOf(GeneratedTable.COUNTRY_ENUM),
        replaces = "kotlinx-locale-country-types",
    ),

    /**
     * The `Currency` enum, narrowed to the codes `currency { entries(...) }` names.
     *
     * Carries the country-to-currency map as well, because it ships in the same
     * artifact this replaces and `Country.currency` reads it. Generating the enum
     * without it would exclude `kotlinx-locale-currency-types` and leave nothing
     * behind the lookup.
     */
    CURRENCY_ENTRIES(
        dslName = "currency.entries",
        tables = setOf(GeneratedTable.CURRENCY_ENUM, GeneratedTable.COUNTRY_CURRENCIES),
        replaces = "kotlinx-locale-currency-types",
    ),
    ;

    companion object {

        /** Every table some type can ask for, which is every table that is not locale data. */
        internal val REACHABLE_TABLES: Set<GeneratedTable> = entries.flatMap(LocaleType::tables).toSet()

        /** The group the published artifacts a generated type replaces are published under. */
        internal const val PUBLISHED_GROUP: String = "dev.carcara"
    }
}
