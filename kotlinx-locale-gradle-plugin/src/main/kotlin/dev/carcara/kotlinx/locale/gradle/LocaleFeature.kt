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

import dev.carcara.kotlinx.locale.codegen.GeneratedBinding
import dev.carcara.kotlinx.locale.codegen.GeneratedTable

/**
 * One thing a build can ask to be generated, and everything generating it takes.
 *
 * A feature declares the closure of tables it needs rather than pointing at
 * other features it depends on. The distinction matters. With dependency edges,
 * `number.compact` and `number.plurals` are two switches and one of the four
 * combinations produces a source set that compiles and then picks the wrong
 * plural form, because selecting a compact pattern is a plural selection over
 * the divided value. With closures that combination cannot be written down:
 * asking for compact emits the plural table because compact's table set contains
 * it.
 *
 * So a flag never changes what an API call means. It decides which locales and
 * which tables reach the generated source set. Asking for something not enabled
 * is a compile error, which is the right failure; what must never happen is a
 * configuration that compiles and answers wrongly.
 *
 * [dslName] is carried here so a failure can quote what a user would type rather
 * than the enum constant.
 */
enum class LocaleFeature(val dslName: String, val tables: Set<GeneratedTable>, val bindings: Set<GeneratedBinding>) {

    COUNTRY_NAMES(
        dslName = "country.names",
        tables = setOf(GeneratedTable.COUNTRY_NAMES),
        bindings = setOf(GeneratedBinding.COUNTRY),
    ),

    CURRENCY_NAMES(
        dslName = "currency.names",
        tables = setOf(GeneratedTable.CURRENCY_NAMES),
        bindings = setOf(GeneratedBinding.CURRENCY),
    ),

    /** Includes the name table: a pattern substitutes the symbol into itself. */
    CURRENCY_FORMATS(
        dslName = "currency.formats",
        tables = setOf(GeneratedTable.CURRENCY_NAMES, GeneratedTable.CURRENCY_FORMATS),
        bindings = setOf(GeneratedBinding.CURRENCY),
    ),

    DATETIME_PATTERNS(
        dslName = "datetime.patterns",
        tables = setOf(GeneratedTable.DATE_TIME),
        bindings = setOf(GeneratedBinding.DATE_TIME),
    ),

    /**
     * Includes the pattern table: matching a skeleton scores against the
     * locale's standard date and time patterns, and rendering the winner needs
     * its month and weekday names.
     */
    DATETIME_SKELETONS(
        dslName = "datetime.skeletons",
        tables = setOf(GeneratedTable.DATE_TIME, GeneratedTable.SKELETONS),
        bindings = setOf(GeneratedBinding.DATE_TIME, GeneratedBinding.SKELETONS),
    ),

    /**
     * Person names, and the initials derived from them.
     *
     * One table and nothing else: the patterns say where each part of a name
     * goes, and nothing in them needs a number, a date or another locale's
     * names.
     */
    PERSONNAME_FORMATS(
        dslName = "personName.formats",
        tables = setOf(GeneratedTable.PERSON_NAMES),
        bindings = setOf(GeneratedBinding.PERSON_NAME),
    ),

    /**
     * Date and time intervals: `Jul 18 – 22, 2026`.
     *
     * Includes the skeleton tables, because an interval is a split of the
     * pattern the skeleton matcher picks, and the pattern tables the matcher
     * scores against.
     */
    DATETIME_INTERVALS(
        dslName = "datetime.intervals",
        tables = setOf(GeneratedTable.DATE_TIME, GeneratedTable.SKELETONS, GeneratedTable.INTERVAL_FORMATS),
        bindings = setOf(GeneratedBinding.DATE_TIME, GeneratedBinding.SKELETONS, GeneratedBinding.INTERVALS),
    ),

    /**
     * Stand-alone month, weekday and quarter names.
     *
     * Includes the pattern table it reads the format names from, since a
     * stand-alone table stores only the differences.
     */
    DATETIME_STANDALONE(
        dslName = "datetime.standalone",
        tables = setOf(GeneratedTable.DATE_TIME, GeneratedTable.DATE_TIME_STANDALONE),
        bindings = setOf(GeneratedBinding.DATE_TIME),
    ),

    /**
     * Relative wording: `3 days ago`, `yesterday`.
     *
     * Includes the number and plural tables, because the wording picks a plural
     * form and renders its count in the locale's own digits.
     */
    DATETIME_RELATIVE_TIME(
        dslName = "datetime.relativeTime",
        tables = setOf(GeneratedTable.RELATIVE_TIME, GeneratedTable.NUMBER, GeneratedTable.PLURALS),
        bindings = setOf(GeneratedBinding.RELATIVE_TIME, GeneratedBinding.NUMBER),
    ),

    /**
     * Duration wording: `2 hours`, `2 hr`, `2h`.
     *
     * Carries the number and plural tables for the same reason relative time
     * does, and is a separate feature from it because the two tables are
     * separate: a build that counts things down does not need `yesterday`.
     */
    DATETIME_DURATION_UNITS(
        dslName = "datetime.durationUnits",
        tables = setOf(GeneratedTable.DURATION_UNITS, GeneratedTable.NUMBER, GeneratedTable.PLURALS),
        bindings = setOf(GeneratedBinding.DURATION_UNITS, GeneratedBinding.NUMBER),
    ),

    /**
     * Language, script and region names, and `Locale.displayName`.
     *
     * One feature rather than three, because the display name algorithm reaches
     * all of them: naming `sr-Cyrl-BA` needs the language, the script and the
     * region, and a build that had only the first would compose a name with two
     * raw subtags in it.
     */
    LANGUAGE_NAMES(
        dslName = "language.names",
        tables = setOf(GeneratedTable.LANGUAGE_NAMES),
        bindings = setOf(GeneratedBinding.LANGUAGE),
    ),

    /**
     * Number symbols and the plain decimal and percent patterns.
     *
     * Includes the plural table, because the source object that carries the
     * formats carries the plural rules too and a compact call through it would
     * otherwise pick the wrong pattern.
     */
    NUMBER_FORMATS(
        dslName = "number.formats",
        tables = setOf(GeneratedTable.NUMBER, GeneratedTable.PLURALS),
        bindings = setOf(GeneratedBinding.NUMBER),
    ),

    /**
     * Compact notation: `1.2K`, `1.2 thousand`.
     *
     * Includes the format and plural tables. Compact patterns are keyed by
     * plural category, so selecting one is a plural selection over the divided
     * value, and there is no reading under which compact without plurals is a
     * choice someone meant to make.
     */
    NUMBER_COMPACT(
        dslName = "number.compact",
        tables = setOf(GeneratedTable.NUMBER, GeneratedTable.NUMBER_COMPACT, GeneratedTable.PLURALS),
        bindings = setOf(GeneratedBinding.NUMBER),
    ),

    /**
     * CLDR plural rules on their own, for a caller choosing between translated
     * strings rather than formatting a number.
     */
    NUMBER_PLURALS(
        dslName = "number.plurals",
        tables = setOf(GeneratedTable.NUMBER, GeneratedTable.PLURALS),
        bindings = setOf(GeneratedBinding.NUMBER),
    ),

    /**
     * Ordinal forms: `1st`, `1.`, `1º`.
     *
     * Includes the plural table, because eight of CLDR's ordinal rule closures
     * select their suffix by ordinal plural category. English is the obvious
     * one: its rule is literally `$(ordinal,one{st}two{nd}few{rd}other{th})$`.
     */
    NUMBER_ORDINALS(
        dslName = "number.ordinals",
        tables = setOf(GeneratedTable.NUMBER, GeneratedTable.ORDINALS, GeneratedTable.PLURALS),
        bindings = setOf(GeneratedBinding.NUMBER),
    ),

    /**
     * The localized GMT format: `GMT-08:00` in the locale's own word and digits.
     *
     * Nine short strings per locale, and the fallback every other zone style
     * degrades to. Includes the number tables, because the offset is written
     * with the locale's own digits, and the plural table with them: the number
     * binding carries the plural rules whatever it was asked for, so a closure
     * that named the number table alone would emit a source file referring to a
     * registry nothing wrote.
     */
    TIMEZONE_FORMATS(
        dslName = "timezone.formats",
        tables = setOf(GeneratedTable.TIME_ZONE_FORMATS, GeneratedTable.NUMBER, GeneratedTable.PLURALS),
        bindings = setOf(GeneratedBinding.TIME_ZONE, GeneratedBinding.NUMBER),
    ),

    /**
     * Zone and metazone display names: `Pacific Standard Time`, `PT`.
     *
     * Includes the format table, which every one of them falls back to.
     */
    TIMEZONE_NAMES(
        dslName = "timezone.names",
        tables = setOf(
            GeneratedTable.TIME_ZONE_FORMATS,
            GeneratedTable.TIME_ZONE_NAMES,
            GeneratedTable.NUMBER,
            GeneratedTable.PLURALS,
        ),
        bindings = setOf(GeneratedBinding.TIME_ZONE, GeneratedBinding.NUMBER),
    ),

    /**
     * Exemplar cities, for the generic location format: `Los Angeles Time`.
     *
     * Worth asking for deliberately. Across all locales this is the largest zone
     * table by a wide margin, and without it the location format falls back to
     * the identifier's own last part, which is the degradation UTS #35
     * prescribes rather than a failure.
     *
     * Does not drag in the country names. A zone in a single-zone region is
     * named for the region, and without those names that reads as the region
     * code, which is again the spec's own fallback. Whether a spelled-out
     * country name is worth 400 KB of tables is the consumer's call.
     */
    TIMEZONE_EXEMPLAR_CITIES(
        dslName = "timezone.exemplarCities",
        tables = setOf(
            GeneratedTable.TIME_ZONE_FORMATS,
            GeneratedTable.TIME_ZONE_NAMES,
            GeneratedTable.TIME_ZONE_CITIES,
            GeneratedTable.NUMBER,
            GeneratedTable.PLURALS,
        ),
        bindings = setOf(GeneratedBinding.TIME_ZONE, GeneratedBinding.TIME_ZONE_CITIES, GeneratedBinding.NUMBER),
    ),

    /**
     * Compact money: `$1.2M`.
     *
     * Includes the currency name and pattern tables it formats through, and the
     * plural table its own patterns are keyed by.
     */
    CURRENCY_COMPACT(
        dslName = "currency.compact",
        tables = setOf(
            GeneratedTable.CURRENCY_NAMES,
            GeneratedTable.CURRENCY_FORMATS,
            GeneratedTable.CURRENCY_COMPACT,
            GeneratedTable.NUMBER,
            GeneratedTable.PLURALS,
        ),
        bindings = setOf(GeneratedBinding.CURRENCY, GeneratedBinding.NUMBER),
    ),

    /**
     * Currency names that agree with a count: `2 US dollars`.
     *
     * Includes the currency name table it falls back to when a locale spells no
     * form for the category, and the plural table that picks the category. Not
     * the currency pattern table: the name form writes the number the way the
     * locale writes any number, so no `¤` pattern is involved.
     */
    CURRENCY_PLURAL_NAMES(
        dslName = "currency.pluralNames",
        tables = setOf(
            GeneratedTable.CURRENCY_NAMES,
            GeneratedTable.CURRENCY_PLURAL_NAMES,
            GeneratedTable.NUMBER,
            GeneratedTable.PLURALS,
        ),
        bindings = setOf(GeneratedBinding.CURRENCY, GeneratedBinding.CURRENCY_PLURALS, GeneratedBinding.NUMBER),
    ),
    ;

    companion object {

        /** Every table some feature can ask for, which should be every table there is. */
        internal val REACHABLE_TABLES: Set<GeneratedTable> = entries.flatMap(LocaleFeature::tables).toSet()
    }
}
