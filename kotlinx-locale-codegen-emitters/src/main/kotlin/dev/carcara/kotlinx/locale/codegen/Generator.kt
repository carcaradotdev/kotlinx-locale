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

package dev.carcara.kotlinx.locale.codegen

import dev.carcara.kotlinx.locale.codegen.pipeline.KeyElisionCodec
import dev.carcara.kotlinx.locale.codegen.pipeline.PayloadCodec
import dev.carcara.kotlinx.locale.codegen.pipeline.PayloadShape
import java.io.File

/**
 * Where each piece of generated source goes.
 *
 * A missing root means "do not generate this", which is how the Gradle plugin
 * turns features off and how a caller that only wants data skips the enums.
 * Every root is a package root: the emitters append their own package path.
 */
public class BindingTarget(
    /** The package root the file is written under. */
    public val root: File,
    /** The package the object and its extensions live in. */
    public val packageName: String,
    /** The object's name, e.g. `CldrCountry` or `GeneratedCountryNames`. */
    public val objectName: String,
) {
    public companion object
}

/**
 * Every table an emitter can write, and so every root a caller can ask for.
 *
 * An enum rather than a field per table because the three places that name a
 * table — the root, the registry package and the plugin's feature mapping — then
 * grow by one row rather than by one line each, and the compiler can check that
 * a package exists for every table.
 */
public enum class GeneratedTable {
    LOCALE_CATALOG,
    COUNTRY_ENUM,
    COUNTRY_CURRENCIES,
    CURRENCY_ENUM,
    COUNTRY_NAMES,
    CURRENCY_FORMATS,
    CURRENCY_NAMES,

    /** The count-keyed currency names behind `2 US dollars`, and the patterns that join them to a number. */
    CURRENCY_PLURAL_NAMES,
    DATE_TIME,

    /** The three skeleton tables, which travel together. */
    SKELETONS,

    /** Interval patterns, keyed by skeleton id and greatest-difference field. */
    INTERVAL_FORMATS,

    /** Stand-alone month, weekday and quarter names, where they differ from the format ones. */
    DATE_TIME_STANDALONE,

    /** Language, script and region names, and the patterns that compose them. */
    LANGUAGE_NAMES,

    /** "3 days ago" and its wording per locale. */
    RELATIVE_TIME,

    /** "2 hours", "2 hr", "2h": the `duration-*` measurement units per locale. */
    DURATION_UNITS,

    /** The nine zone format strings, and the locale-independent zone metadata. */
    TIME_ZONE_FORMATS,

    /** Zone and metazone display names. */
    TIME_ZONE_NAMES,

    /** Exemplar cities, which are the largest zone table and ship on their own. */
    TIME_ZONE_CITIES,

    /** Number symbols and the plain decimal and percent patterns. */
    NUMBER,

    /** The short and long compact decimal tables. */
    NUMBER_COMPACT,

    /**
     * The compact currency table.
     *
     * Its own table rather than part of [NUMBER_COMPACT] so that it ships in the
     * currency artifact: a consumer formatting chart labels should not carry
     * money patterns, and a consumer formatting money should not have to reach
     * into the number artifact's internals for them.
     */
    CURRENCY_COMPACT,

    /** Person name patterns: the forty-two cells of UTS #35 Part 8. */
    PERSON_NAMES,

    /** Cardinal and ordinal plural rules, shared by id across locales. */
    PLURALS,

    /** The rule closures behind `1st` and `1.`, shared the same way. */
    ORDINALS,
    ;

    public companion object
}

/** Every source object the binding emitter can write, and the suffix its name takes. */
public enum class GeneratedBinding(public val objectSuffix: String) {
    COUNTRY("CountryNames"),
    CURRENCY("Currency"),

    /**
     * Needs [CURRENCY] too: the name form falls back to the count-less display
     * name, which lives in the currency binding's table rather than in this one.
     */
    CURRENCY_PLURALS("CurrencyPlurals"),
    DATE_TIME("DateTime"),

    /**
     * Needs [DATE_TIME] too: a skeleton binding reads the pattern table through
     * it rather than carrying a copy.
     */
    SKELETONS("DateTimeSkeletons"),

    /**
     * Needs [SKELETONS] too: an interval has to be given a pattern for the
     * requested skeleton before it can be split, and the matcher is shared.
     */
    INTERVALS("DateTimeIntervals"),
    NUMBER("Number"),
    LANGUAGE("LanguageNames"),
    RELATIVE_TIME("RelativeTime"),
    DURATION_UNITS("DurationUnits"),
    TIME_ZONE("TimeZone"),
    TIME_ZONE_CITIES("TimeZoneCities"),
    PERSON_NAME("PersonName"),
    ;

    public companion object
}

public class SourceRoots private constructor(
    private val tables: Map<GeneratedTable, File>,
    private val bindings: Map<GeneratedBinding, BindingTarget>,
) {

    public operator fun get(table: GeneratedTable): File? = tables[table]

    public operator fun get(binding: GeneratedBinding): BindingTarget? = bindings[binding]

    public class Builder {
        private val tables = LinkedHashMap<GeneratedTable, File>()
        private val bindings = LinkedHashMap<GeneratedBinding, BindingTarget>()

        /** A null root is ignored, so a caller can pass a feature flag straight through. */
        public fun table(table: GeneratedTable, root: File?): Builder = apply {
            if (root != null) tables[table] = root
        }

        public fun binding(binding: GeneratedBinding, target: BindingTarget?): Builder = apply {
            if (target != null) bindings[binding] = target
        }

        public fun build(): SourceRoots = SourceRoots(tables, bindings)

        public companion object
    }

    public companion object
}

/**
 * The package the payload registries are written into.
 *
 * The shipped modules use the library's own, so their registries stay internal
 * to the artifact that owns them. A build generating its own narrowed data uses
 * its own, so the two can sit on one classpath.
 */
public class RegistryPackages private constructor(
    private val byTable: Map<GeneratedTable, String>,
    /**
     * Where the locale catalog enums go.
     *
     * Here rather than in [byTable] because it is not a registry: the catalog is
     * the public `PT.BR`, not an internal table behind a source object. It is the
     * only generated public type whose package can move at all. `Country` and
     * `Currency` cannot, because `kotlinx-locale-country-core` and
     * `kotlinx-locale-currency-core` declare their extensions on those exact
     * names, so a generated one has to take the same package and the shipped
     * artifact has to be off the classpath.
     */
    public val catalog: String,
) {

    public operator fun get(table: GeneratedTable): String = byTable[table] ?: error("no registry package declared for $table")

    public companion object {

        /** What the published `-cldr-full` artifacts use. */
        public val SHIPPED: RegistryPackages = RegistryPackages(
            catalog = "dev.carcara.kotlinx.locale.catalog",
            byTable = mapOf(
                GeneratedTable.COUNTRY_NAMES to "dev.carcara.kotlinx.locale.country.cldr.internal.data",
                GeneratedTable.CURRENCY_FORMATS to "dev.carcara.kotlinx.locale.currency.cldr.internal.data",
                GeneratedTable.CURRENCY_NAMES to "dev.carcara.kotlinx.locale.currency.cldr.internal.data",
                GeneratedTable.CURRENCY_PLURAL_NAMES to "dev.carcara.kotlinx.locale.currency.cldr.plurals.internal.data",
                GeneratedTable.DATE_TIME to "dev.carcara.kotlinx.locale.datetime.cldr.internal.data",
                GeneratedTable.DATE_TIME_STANDALONE to "dev.carcara.kotlinx.locale.datetime.cldr.internal.data",
                GeneratedTable.SKELETONS to "dev.carcara.kotlinx.locale.datetime.cldr.skeletons.internal.data",
                GeneratedTable.INTERVAL_FORMATS to "dev.carcara.kotlinx.locale.datetime.cldr.intervals.internal.data",
                GeneratedTable.NUMBER to "dev.carcara.kotlinx.locale.number.cldr.internal.data",
                GeneratedTable.NUMBER_COMPACT to "dev.carcara.kotlinx.locale.number.cldr.internal.data",
                GeneratedTable.CURRENCY_COMPACT to "dev.carcara.kotlinx.locale.currency.cldr.internal.data",
                GeneratedTable.PERSON_NAMES to "dev.carcara.kotlinx.locale.personname.cldr.internal.data",
                GeneratedTable.PLURALS to "dev.carcara.kotlinx.locale.number.cldr.internal.data",
                GeneratedTable.ORDINALS to "dev.carcara.kotlinx.locale.number.cldr.internal.data",
                GeneratedTable.LANGUAGE_NAMES to "dev.carcara.kotlinx.locale.language.cldr.internal.data",
                GeneratedTable.RELATIVE_TIME to "dev.carcara.kotlinx.locale.datetime.cldr.relative.internal.data",
                GeneratedTable.DURATION_UNITS to "dev.carcara.kotlinx.locale.datetime.cldr.durations.internal.data",
                GeneratedTable.TIME_ZONE_FORMATS to "dev.carcara.kotlinx.locale.timezone.cldr.internal.data",
                GeneratedTable.TIME_ZONE_NAMES to "dev.carcara.kotlinx.locale.timezone.cldr.internal.data",
                GeneratedTable.TIME_ZONE_CITIES to "dev.carcara.kotlinx.locale.timezone.cldr.cities.internal.data",
            ),
        )

        /** Everything under one package, which is what a generated source set wants. */
        public fun under(basePackage: String): RegistryPackages = RegistryPackages(
            catalog = "$basePackage.catalog",
            byTable = GeneratedTable.entries.associateWith { "$basePackage.internal.data" },
        )
    }
}

/**
 * A payload table whose emission is entirely described by these six strings.
 *
 * Every locale-keyed table is the same work with different names, so they are a
 * list rather than a block each. What is not uniform — the enums, the catalog
 * and the bindings — stays written out below, because pretending it is uniform
 * would cost more than it saves.
 */
private class PayloadTableSpec(
    val table: GeneratedTable,
    val section: String,
    val filePrefix: String,
    val constPrefix: String,
    val registryProperty: String,
    val versionConstName: String? = null,
)

/**
 * The file name prefixes each table is emitted under.
 *
 * Public so a test can assert that asking for a feature produced every table
 * that feature declared, without keeping a second list of file names that would
 * drift from this one. The four tables absent here are the enums and the
 * catalog, which are emitted from their own paths rather than as keyed payloads.
 */
public val GeneratedTable.emittedFilePrefixes: List<String>
    get() = when (this) {
        GeneratedTable.LOCALE_CATALOG -> listOf("LocaleCatalog")
        GeneratedTable.COUNTRY_ENUM -> listOf("Country")
        GeneratedTable.COUNTRY_CURRENCIES -> listOf("CountryCurrencies")
        GeneratedTable.CURRENCY_ENUM -> listOf("Currency")
        else -> PAYLOAD_TABLES.filter { it.table == this }.map { it.filePrefix }
    }

private val PAYLOAD_TABLES = listOf(
    PayloadTableSpec(
        GeneratedTable.DATE_TIME,
        "dateTime",
        "LocaleData",
        "LOCALE",
        "localeDataRegistry",
        "LOCALE_DATA_CLDR_VERSION",
    ),
    PayloadTableSpec(
        GeneratedTable.COUNTRY_NAMES,
        "countryNames",
        "CountryNames",
        "COUNTRY_NAMES",
        "countryNamesRegistry",
        "COUNTRY_NAMES_CLDR_VERSION",
    ),
    PayloadTableSpec(
        GeneratedTable.CURRENCY_FORMATS,
        "currencyFormats",
        "CurrencyFormats",
        "CURRENCY_FORMATS",
        "currencyFormatsRegistry",
        "CURRENCY_FORMATS_CLDR_VERSION",
    ),
    PayloadTableSpec(
        GeneratedTable.CURRENCY_NAMES,
        "currencyNames",
        "CurrencyNames",
        "CURRENCY_NAMES",
        "currencyNamesRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.CURRENCY_PLURAL_NAMES,
        "currencyPluralNames",
        "CurrencyPluralNames",
        "CURRENCY_PLURAL_NAMES",
        "currencyPluralNamesRegistry",
        "CURRENCY_PLURAL_NAMES_CLDR_VERSION",
    ),
    // Three tables rather than one record: a locale's own availableFormats is the
    // bulk of it, its append formats are almost always root's, and its names sit
    // somewhere between. Deduplicating them together would cost the two small
    // ones the saving they have.
    PayloadTableSpec(
        GeneratedTable.SKELETONS,
        "skeletonFormats",
        "SkeletonFormats",
        "SKELETON_FORMATS",
        "skeletonFormatsRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.SKELETONS,
        "skeletonAppendFormats",
        "SkeletonAppendFormats",
        "SKELETON_APPEND_FORMATS",
        "skeletonAppendFormatsRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.SKELETONS,
        "skeletonNames",
        "SkeletonNames",
        "SKELETON_NAMES",
        "skeletonNamesRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.PERSON_NAMES,
        "personNames",
        "PersonNames",
        "PERSON_NAMES",
        "personNamesRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.INTERVAL_FORMATS,
        "intervalFormats",
        "IntervalFormats",
        "INTERVAL_FORMATS",
        "intervalFormatsRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.DATE_TIME_STANDALONE,
        "dateTimeStandalone",
        "LocaleStandalone",
        "LOCALE_STANDALONE",
        "localeStandaloneRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.TIME_ZONE_FORMATS,
        "timeZoneFormats",
        "TimeZoneFormats",
        "TIME_ZONE_FORMATS",
        "timeZoneFormatsRegistry",
        "TIME_ZONE_CLDR_VERSION",
    ),
    PayloadTableSpec(
        GeneratedTable.TIME_ZONE_NAMES,
        "timeZoneNames",
        "TimeZoneNames",
        "TIME_ZONE_NAMES",
        "timeZoneNamesRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.TIME_ZONE_CITIES,
        "timeZoneCities",
        "TimeZoneCities",
        "TIME_ZONE_CITIES",
        "timeZoneCitiesRegistry",
        "TIME_ZONE_CITIES_CLDR_VERSION",
    ),
    PayloadTableSpec(
        GeneratedTable.RELATIVE_TIME,
        "relativeTime",
        "RelativeTime",
        "RELATIVE_TIME",
        "relativeTimeRegistry",
        "RELATIVE_TIME_CLDR_VERSION",
    ),
    PayloadTableSpec(
        GeneratedTable.DURATION_UNITS,
        "durationUnits",
        "DurationUnits",
        "DURATION_UNITS",
        "durationUnitsRegistry",
        "DURATION_UNITS_CLDR_VERSION",
    ),
    PayloadTableSpec(
        GeneratedTable.LANGUAGE_NAMES,
        "localeDisplayNames",
        "LocaleDisplayNames",
        "LOCALE_DISPLAY_NAMES",
        "localeDisplayNamesRegistry",
        "LANGUAGE_NAMES_CLDR_VERSION",
    ),
    PayloadTableSpec(
        GeneratedTable.NUMBER,
        "numberSymbols",
        "NumberSymbols",
        "NUMBER_SYMBOLS",
        "numberSymbolsRegistry",
        "NUMBER_CLDR_VERSION",
    ),
    PayloadTableSpec(
        GeneratedTable.NUMBER,
        "numberPatterns",
        "NumberPatterns",
        "NUMBER_PATTERNS",
        "numberPatternsRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.NUMBER_COMPACT,
        "numberCompactShort",
        "CompactShort",
        "COMPACT_SHORT",
        "compactShortRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.NUMBER_COMPACT,
        "numberCompactLong",
        "CompactLong",
        "COMPACT_LONG",
        "compactLongRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.CURRENCY_COMPACT,
        "currencyCompactShort",
        "CurrencyCompact",
        "CURRENCY_COMPACT",
        "currencyCompactRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.PLURALS,
        "pluralRuleSets",
        "PluralRuleSets",
        "PLURAL_RULES",
        "pluralRuleSetsRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.PLURALS,
        "pluralRuleIndex",
        "PluralRuleIndex",
        "PLURAL_INDEX",
        "pluralRuleIndexRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.ORDINALS,
        "ordinalRuleSets",
        "OrdinalRuleSets",
        "ORDINAL_RULES",
        "ordinalRuleSetsRegistry",
    ),
    PayloadTableSpec(
        GeneratedTable.ORDINALS,
        "ordinalRuleIndex",
        "OrdinalRuleIndex",
        "ORDINAL_INDEX",
        "ordinalRuleIndexRegistry",
    ),
)

/**
 * Writes Kotlin sources for everything [roots] asks for.
 *
 * This is the single code path the shipped artifacts and the Gradle plugin both
 * run. They differ in which roots they pass and which package the registries
 * land in, never in what is written into them, which is what keeps the two from
 * drifting. `BundleRoundTripTest` pins that down by regenerating the shipped
 * sources from the published bundle and comparing byte for byte.
 *
 * A requested table whose bundle section is empty is an error rather than an
 * empty registry. An empty registry compiles and then answers `null` for every
 * locale, which is the failure this is worth a check to avoid.
 */
/**
 * Which codec a section is written with when the caller does not say.
 *
 * Only the sparse sections, and only because those are the ones every reader
 * reaches through `sparseRecordValue`. A resolved table is parsed by whichever
 * source owns it, sometimes positionally, so eliding its keys would mean
 * teaching each of those readers the new shape one at a time. These six are also
 * where the keys are: they carry the zone ids, the ISO codes and the BCP-47
 * subtags that every locale repeats.
 */
public fun defaultCodecFor(section: String): PayloadCodec =
    if (BundleSection.BY_NAME[section]?.isSparse == true) KeyElisionCodec() else PayloadCodec.Identity

public fun generateSources(
    bundle: LocaleDataBundle,
    roots: SourceRoots,
    packages: RegistryPackages = RegistryPackages.SHIPPED,
    /**
     * Which codec each section is written with, by section name.
     *
     * A function rather than a map because the interesting answer is per
     * section: keys are two thirds of the time-zone tables and a tenth of the
     * language ones, so one codec is not the right answer everywhere. The
     * default rewrites nothing, which is what keeps adding the pipeline a no-op
     * until a build asks for something.
     */
    codecs: (String) -> PayloadCodec = ::defaultCodecFor,
) {
    val cldr = bundle.cldrVersion

    for (spec in PAYLOAD_TABLES) {
        val root = roots[spec.table] ?: continue
        val payloads = bundle.section(spec.section)
        check(payloads.isNotEmpty()) {
            "the bundle carries no '${spec.section}' section, but ${spec.table} was asked for. " +
                "Either the bundle predates that section or generation skipped it."
        }
        val section = BundleSection.BY_NAME[spec.section]
        KeyedPayloadEmitter(
            outputDir = root.packageDir(packages[spec.table]),
            packageName = packages[spec.table],
            filePrefix = spec.filePrefix,
            constPrefix = spec.constPrefix,
            registryProperty = spec.registryProperty,
            source = "CLDR $cldr",
            versionConst = spec.versionConstName?.let { it to cldr },
            // The section already declares where the fields are, so the codec is
            // told the same thing the runtime decoder is told, from one place.
            shape = PayloadShape(section?.sparseFields ?: 0),
            codec = codecs(spec.section),
        ).emit(payloads)
    }

    roots[GeneratedTable.LOCALE_CATALOG]?.let { root ->
        emitLocaleCatalog(
            outputDir = root.packageDir(packages.catalog),
            cldrTag = cldr,
            localeTags = bundle.localeTags,
            packageName = packages.catalog,
        )
    }

    roots[GeneratedTable.COUNTRY_ENUM]?.let { root ->
        emitCountryEnum(
            outputFile = root.packageDir("dev.carcara.kotlinx.locale.country").resolve("Country.kt"),
            cldrTag = cldr,
            countries = bundle.countries,
        )
    }

    roots[GeneratedTable.CURRENCY_ENUM]?.let { root ->
        emitCurrencyEnum(
            outputFile = root.packageDir("dev.carcara.kotlinx.locale.currency").resolve("Currency.kt"),
            cldrTag = cldr,
            isoPublished = bundle.isoPublished,
            currencies = bundle.currencies,
        )
    }

    roots[GeneratedTable.COUNTRY_CURRENCIES]?.let { root ->
        emitCountryCurrencies(
            outputFile = root.packageDir("dev.carcara.kotlinx.locale.currency.internal")
                .resolve("CountryCurrencies.kt"),
            cldrTag = cldr,
            mapping = bundle.countryCurrencies,
        )
    }

    roots[GeneratedBinding.COUNTRY]?.let { target ->
        emitCountryBinding(target.root, target.spec(packages[GeneratedTable.COUNTRY_NAMES], cldr))
    }

    roots[GeneratedBinding.CURRENCY]?.let { target ->
        val number = roots[GeneratedBinding.NUMBER]
        emitCurrencyBinding(
            target.root,
            target.spec(packages[GeneratedTable.CURRENCY_NAMES], cldr),
            // Compact money is keyed by plural category, so the table is only
            // wired in when the plural rules that select from it are there too.
            numberObject = if (roots[GeneratedTable.CURRENCY_COMPACT] != null && number != null) {
                number.packageName + "." + number.objectName
            } else {
                null
            },
            // The symbols can be asked for without the patterns, and then there
            // is no format entry point rather than one over a table nothing wrote.
            hasFormats = roots[GeneratedTable.CURRENCY_FORMATS] != null,
        )
    }

    roots[GeneratedBinding.CURRENCY_PLURALS]?.let { target ->
        val currency = requireNotNull(roots[GeneratedBinding.CURRENCY]) {
            "a currency name with no plural form of its own reads the count-less one, so it needs the currency binding"
        }
        val number = requireNotNull(roots[GeneratedBinding.NUMBER]) {
            "a currency name agrees with the count, so it needs the plural rules the number binding carries"
        }
        emitCurrencyPluralsBinding(
            target.root,
            target.spec(packages[GeneratedTable.CURRENCY_PLURAL_NAMES], cldr),
            currencyObject = currency.packageName + "." + currency.objectName,
            numberObject = number.packageName + "." + number.objectName,
        )
    }

    roots[GeneratedBinding.DATE_TIME]?.let { target ->
        emitDateTimeBinding(
            target.root,
            target.spec(packages[GeneratedTable.DATE_TIME], cldr),
            hasStandalone = roots[GeneratedTable.DATE_TIME_STANDALONE] != null,
            weekData = bundle.tables[BundleTables.WEEK_DATA].orEmpty(),
        )
    }

    roots[GeneratedBinding.NUMBER]?.let { target ->
        emitNumberBinding(
            target.root,
            target.spec(packages[GeneratedTable.NUMBER], cldr),
            hasCompact = roots[GeneratedTable.NUMBER_COMPACT] != null,
            hasOrdinals = roots[GeneratedTable.ORDINALS] != null,
        )
    }

    roots[GeneratedBinding.LANGUAGE]?.let { target ->
        emitLanguageBinding(target.root, target.spec(packages[GeneratedTable.LANGUAGE_NAMES], cldr))
    }

    roots[GeneratedBinding.RELATIVE_TIME]?.let { target ->
        val number = requireNotNull(roots[GeneratedBinding.NUMBER]) {
            "relative wording picks a plural form and renders a count, so it needs the number binding"
        }
        emitRelativeTimeBinding(
            target.root,
            target.spec(packages[GeneratedTable.RELATIVE_TIME], cldr),
            numberObject = number.packageName + "." + number.objectName,
        )
    }

    roots[GeneratedBinding.DURATION_UNITS]?.let { target ->
        val number = requireNotNull(roots[GeneratedBinding.NUMBER]) {
            "duration wording picks a plural form and renders a count, so it needs the number binding"
        }
        emitDurationUnitsBinding(
            target.root,
            target.spec(packages[GeneratedTable.DURATION_UNITS], cldr),
            numberObject = number.packageName + "." + number.objectName,
        )
    }

    roots[GeneratedBinding.TIME_ZONE]?.let { target ->
        emitTimeZoneBinding(
            target.root,
            target.spec(packages[GeneratedTable.TIME_ZONE_FORMATS], cldr),
            metadata = bundle.tables[BundleTables.TIME_ZONE_METADATA].orEmpty(),
            numberObject = roots[GeneratedBinding.NUMBER]?.let { it.packageName + "." + it.objectName },
            hasNames = roots[GeneratedTable.TIME_ZONE_NAMES] != null,
        )
    }

    roots[GeneratedBinding.TIME_ZONE_CITIES]?.let { target ->
        val zones = requireNotNull(roots[GeneratedBinding.TIME_ZONE]) {
            "the exemplar cities layer reads the zone names through the timezone binding, so it needs one"
        }
        emitTimeZoneCitiesBinding(
            target.root,
            target.spec(packages[GeneratedTable.TIME_ZONE_CITIES], cldr),
            timeZoneObject = zones.packageName + "." + zones.objectName,
        )
    }

    roots[GeneratedBinding.SKELETONS]?.let { target ->
        val dateTime = requireNotNull(roots[GeneratedBinding.DATE_TIME]) {
            "a skeleton binding reads its patterns through the datetime binding, so it needs one"
        }
        emitSkeletonBinding(
            target.root,
            target.spec(packages[GeneratedTable.SKELETONS], cldr),
            dateTimeObject = "${dateTime.packageName}.${dateTime.objectName}",
        )
    }

    roots[GeneratedBinding.PERSON_NAME]?.let { target ->
        emitPersonNameBinding(
            target.root,
            target.spec(packages[GeneratedTable.PERSON_NAMES], cldr),
            graphemeBreak = bundle.tables[BundleTables.GRAPHEME_BREAK].orEmpty(),
            wordBreakMid = bundle.tables[BundleTables.WORD_BREAK_MID].orEmpty(),
        )
    }

    roots[GeneratedBinding.INTERVALS]?.let { target ->
        val skeletons = requireNotNull(roots[GeneratedBinding.SKELETONS]) {
            "an interval is a split of the pattern the skeleton matcher picks, so it needs that binding"
        }
        emitIntervalBinding(
            target.root,
            target.spec(packages[GeneratedTable.INTERVAL_FORMATS], cldr),
            skeletonObject = "${skeletons.packageName}.${skeletons.objectName}",
        )
    }
}

private fun BindingTarget.spec(registryPackage: String, cldrTag: String) = BindingSpec(
    packageName = packageName,
    objectName = objectName,
    registryPackage = registryPackage,
    source = "CLDR $cldrTag",
)

private fun File.packageDir(packageName: String): File = resolve(packageName.replace('.', '/'))
