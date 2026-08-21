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

import java.io.File

fun main(args: Array<String>) {
    val mode = args.getOrNull(0) ?: "generate"
    val rootDir = File(args.getOrNull(1) ?: ".").absoluteFile

    when (mode) {
        "clone" -> {
            ensureCloned(rootDir, CLDR_REPO)
            ensureCloned(rootDir, ICU_REPO)
            ensureCloned(rootDir, PHONE_REPO)
        }
        "generate" -> {
            val cldrDir = ensureCloned(rootDir, CLDR_REPO)
            val icuDir = ensureCloned(rootDir, ICU_REPO)
            generate(rootDir, cldrDir, icuDir)
        }
        // Rewrites every shipped source from the committed bundle, with no CLDR
        // clone. What `generate` does minus the extraction, which is what you
        // want when an emitter or a codec changed and the data did not.
        // `BundleRoundTripTest` runs the same path into a temp directory and
        // compares, so the two cannot disagree about what a bundle produces.
        "regenerate" -> {
            val bundle = bundleFile(rootDir).bufferedReader().use(LocaleDataBundle::readFrom)
            generateSources(bundle = bundle, roots = shippedRoots(rootDir))
        }
        else -> error("Unknown mode '$mode'. Use 'clone', 'generate' or 'regenerate'.")
    }
}

/** `<module>/src/<sourceSet>/kotlin`, the package root the emitters write under. */
private fun File.sourceRoot(module: String, sourceSet: String = "commonMain"): File = resolve("$module/src/$sourceSet/kotlin")

/**
 * Every fixture goes to the `commonTest` of the one module that reads it.
 *
 * There used to be a shared `conformance-test-suite` destination here, on the
 * reasoning that any source should be checkable against any fixture. The cost
 * was that the shared module is a project dependency of thirteen modules and
 * compiles into every one of their test binaries, so `country-cldr-full` linked
 * the number goldens and `number-cldr-full` linked the time zone ones. That was
 * 2.9 MB of generated source in each, and it is what the memory note in
 * `gradle.properties` was written about.
 *
 * The phone, person name, interval, currency plural, duration and week fixtures
 * were moved out one at a time as each of them broke something; the rest
 * followed. What stayed in the shared module is the contract a source owes
 * whatever its data came from, which is the part a platform source also has to
 * satisfy and which carries no data at all.
 */
private fun phoneConformanceDir(rootDir: File): File = rootDir
    .sourceRoot("kotlinx-locale-phone-metadata-full", "commonTest")
    .resolve("dev/carcara/kotlinx/locale/phone/conformance")

/** Where the person name fixture goes, which is this domain's own tests for the phone reason. */
private fun personNameConformanceDir(rootDir: File): File = rootDir
    .sourceRoot("kotlinx-locale-personname-cldr-full", "commonTest")
    .resolve("dev/carcara/kotlinx/locale/personname/conformance")

/**
 * Where the interval and week goldens go, which is not the shared module.
 *
 * The same reason the phone fixtures moved: `conformance-test-suite` is compiled
 * into every other module's test binary, and the interval golden alone is 486 KB
 * of source across 905 locales. Adding it there was enough to exhaust the
 * Kotlin/Native linker while linking the shared module for Linux.
 */
private fun intervalConformanceDir(rootDir: File): File = rootDir
    .sourceRoot("kotlinx-locale-datetime-cldr-intervals", "commonTest")
    .resolve("dev/carcara/kotlinx/locale/datetime/cldr/intervals/conformance")

/** Where the currency plural fixture goes, which is its own artifact's tests for the interval reason. */
private fun currencyPluralConformanceDir(rootDir: File): File = rootDir
    .sourceRoot("kotlinx-locale-currency-cldr-plurals", "commonTest")
    .resolve("dev/carcara/kotlinx/locale/currency/cldr/plurals/conformance")

private fun durationUnitConformanceDir(rootDir: File): File = rootDir
    .sourceRoot("kotlinx-locale-datetime-cldr-durations", "commonTest")
    .resolve("dev/carcara/kotlinx/locale/datetime/cldr/durations/conformance")

private fun weekConformanceDir(rootDir: File): File = rootDir
    .sourceRoot("kotlinx-locale-datetime-cldr-full", "commonTest")
    .resolve("dev/carcara/kotlinx/locale/datetime/cldr/conformance")

private fun countryConformanceDir(rootDir: File): File = rootDir
    .sourceRoot("kotlinx-locale-country-cldr-full", "commonTest")
    .resolve("dev/carcara/kotlinx/locale/country/conformance")

private fun currencyConformanceDir(rootDir: File): File = rootDir
    .sourceRoot("kotlinx-locale-currency-cldr-full", "commonTest")
    .resolve("dev/carcara/kotlinx/locale/currency/conformance")

private fun numberConformanceDir(rootDir: File): File = rootDir
    .sourceRoot("kotlinx-locale-number-cldr-full", "commonTest")
    .resolve("dev/carcara/kotlinx/locale/number/conformance")

private fun skeletonConformanceDir(rootDir: File): File = rootDir
    .sourceRoot("kotlinx-locale-datetime-cldr-skeletons", "commonTest")
    .resolve("dev/carcara/kotlinx/locale/datetime/cldr/skeletons/conformance")

private fun timeZoneConformanceDir(rootDir: File): File = rootDir
    .sourceRoot("kotlinx-locale-timezone-cldr-cities", "commonTest")
    .resolve("dev/carcara/kotlinx/locale/timezone/conformance")

/**
 * The grapheme break cases, which belong to `kotlinx-locale-core`.
 *
 * The code they exercise is `core/internal/GraphemeClusters.kt`. The cases sat
 * in the person name module for as long as person names were the only caller,
 * which made a failure in the segmenter read as a failure in name formatting.
 */
private fun coreConformanceDir(rootDir: File): File = rootDir
    .sourceRoot("kotlinx-locale-core", "commonTest")
    .resolve("dev/carcara/kotlinx/locale/conformance")

/** Where the published bundle lives, as a resource inside kotlinx-locale-codegen-data. */
internal fun bundleFile(rootDir: File): File = rootDir
    .resolve("kotlinx-locale-codegen-data/src/main/resources")
    .resolve("dev/carcara/kotlinx/locale/cldr-data.txt")

private fun generate(rootDir: File, cldrDir: File, icuDir: File) {
    val bundle = extractBundle(rootDir, cldrDir, icuDir)

    val bundleFile = bundleFile(rootDir)
    bundleFile.parentFile.mkdirs()
    bundleFile.bufferedWriter().use(bundle::writeTo)
    println("[codegen] wrote the CLDR bundle (${bundleFile.length() / 1024} KB) to $bundleFile")

    // The same call the Gradle plugin makes, with the shipped roots and packages.
    generateSources(bundle, shippedRoots(rootDir))

    println("[codegen] done")
}

/** Every published artifact that carries generated source, and where its package root is. */
internal fun shippedRoots(rootDir: File): SourceRoots = SourceRoots.Builder()
    .table(GeneratedTable.LOCALE_CATALOG, rootDir.sourceRoot("kotlinx-locale-types"))
    .table(GeneratedTable.COUNTRY_ENUM, rootDir.sourceRoot("kotlinx-locale-country-types"))
    .table(GeneratedTable.COUNTRY_NAMES, rootDir.sourceRoot("kotlinx-locale-territory-cldr-full"))
    .table(GeneratedTable.CURRENCY_ENUM, rootDir.sourceRoot("kotlinx-locale-currency-types"))
    .table(GeneratedTable.COUNTRY_CURRENCIES, rootDir.sourceRoot("kotlinx-locale-currency-types"))
    .table(GeneratedTable.CURRENCY_FORMATS, rootDir.sourceRoot("kotlinx-locale-currency-cldr-full"))
    .table(GeneratedTable.CURRENCY_NAMES, rootDir.sourceRoot("kotlinx-locale-currency-cldr-full"))
    .table(GeneratedTable.CURRENCY_PLURAL_NAMES, rootDir.sourceRoot("kotlinx-locale-currency-cldr-plurals"))
    .table(GeneratedTable.DATE_TIME, rootDir.sourceRoot("kotlinx-locale-datetime-cldr-full"))
    .table(GeneratedTable.SKELETONS, rootDir.sourceRoot("kotlinx-locale-datetime-cldr-skeletons"))
    .table(GeneratedTable.INTERVAL_FORMATS, rootDir.sourceRoot("kotlinx-locale-datetime-cldr-intervals"))
    .table(GeneratedTable.DATE_TIME_STANDALONE, rootDir.sourceRoot("kotlinx-locale-datetime-cldr-full"))
    .table(GeneratedTable.LANGUAGE_NAMES, rootDir.sourceRoot("kotlinx-locale-language-cldr-full"))
    .table(GeneratedTable.RELATIVE_TIME, rootDir.sourceRoot("kotlinx-locale-datetime-cldr-relative"))
    .table(GeneratedTable.DURATION_UNITS, rootDir.sourceRoot("kotlinx-locale-datetime-cldr-durations"))
    .table(GeneratedTable.PERSON_NAMES, rootDir.sourceRoot("kotlinx-locale-personname-cldr-full"))
    .table(GeneratedTable.TIME_ZONE_FORMATS, rootDir.sourceRoot("kotlinx-locale-timezone-cldr-full"))
    .table(GeneratedTable.TIME_ZONE_NAMES, rootDir.sourceRoot("kotlinx-locale-timezone-cldr-full"))
    .table(GeneratedTable.TIME_ZONE_CITIES, rootDir.sourceRoot("kotlinx-locale-timezone-cldr-cities"))
    .table(GeneratedTable.NUMBER, rootDir.sourceRoot("kotlinx-locale-number-cldr-full"))
    .table(GeneratedTable.NUMBER_COMPACT, rootDir.sourceRoot("kotlinx-locale-number-cldr-full"))
    .table(GeneratedTable.CURRENCY_COMPACT, rootDir.sourceRoot("kotlinx-locale-currency-cldr-full"))
    .table(GeneratedTable.PLURALS, rootDir.sourceRoot("kotlinx-locale-number-cldr-full"))
    .table(GeneratedTable.ORDINALS, rootDir.sourceRoot("kotlinx-locale-number-cldr-full"))
    // The source objects and their convenience extensions come from the same
    // emitter the Gradle plugin uses, so a narrowed build and a full one cannot
    // present a different API.
    .binding(
        GeneratedBinding.COUNTRY,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-country-cldr-full"),
            packageName = "dev.carcara.kotlinx.locale.country.cldr",
            objectName = "CldrCountry",
        ),
    )
    .binding(
        GeneratedBinding.CURRENCY,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-currency-cldr-full"),
            packageName = "dev.carcara.kotlinx.locale.currency.cldr",
            objectName = "CldrCurrency",
        ),
    )
    .binding(
        GeneratedBinding.CURRENCY_PLURALS,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-currency-cldr-plurals"),
            packageName = "dev.carcara.kotlinx.locale.currency.cldr.plurals",
            objectName = "CldrCurrencyPlurals",
        ),
    )
    .binding(
        GeneratedBinding.DATE_TIME,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-datetime-cldr-full"),
            packageName = "dev.carcara.kotlinx.locale.datetime.cldr",
            objectName = "CldrDateTime",
        ),
    )
    .binding(
        GeneratedBinding.SKELETONS,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-datetime-cldr-skeletons"),
            packageName = "dev.carcara.kotlinx.locale.datetime.cldr.skeletons",
            objectName = "CldrDateTimeSkeletons",
        ),
    )
    .binding(
        GeneratedBinding.INTERVALS,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-datetime-cldr-intervals"),
            packageName = "dev.carcara.kotlinx.locale.datetime.cldr.intervals",
            objectName = "CldrDateTimeIntervals",
        ),
    )
    .binding(
        GeneratedBinding.LANGUAGE,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-language-cldr-full"),
            packageName = "dev.carcara.kotlinx.locale.language.cldr",
            objectName = "CldrLanguage",
        ),
    )
    .binding(
        GeneratedBinding.RELATIVE_TIME,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-datetime-cldr-relative"),
            packageName = "dev.carcara.kotlinx.locale.datetime.cldr.relative",
            objectName = "CldrRelativeTime",
        ),
    )
    .binding(
        GeneratedBinding.DURATION_UNITS,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-datetime-cldr-durations"),
            packageName = "dev.carcara.kotlinx.locale.datetime.cldr.durations",
            objectName = "CldrDurationUnits",
        ),
    )
    .binding(
        GeneratedBinding.TIME_ZONE,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-timezone-cldr-full"),
            packageName = "dev.carcara.kotlinx.locale.timezone.cldr",
            objectName = "CldrTimeZone",
        ),
    )
    .binding(
        GeneratedBinding.TIME_ZONE_CITIES,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-timezone-cldr-cities"),
            packageName = "dev.carcara.kotlinx.locale.timezone.cldr.cities",
            objectName = "CldrTimeZoneCities",
        ),
    )
    .binding(
        GeneratedBinding.PERSON_NAME,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-personname-cldr-full"),
            packageName = "dev.carcara.kotlinx.locale.personname.cldr",
            objectName = "CldrPersonName",
        ),
    )
    .binding(
        GeneratedBinding.NUMBER,
        BindingTarget(
            root = rootDir.sourceRoot("kotlinx-locale-number-cldr-full"),
            packageName = "dev.carcara.kotlinx.locale.number.cldr",
            objectName = "CldrNumber",
        ),
    )
    .build()

/**
 * Reads CLDR and ICU once and resolves everything the emitters need.
 *
 * This is the half that cannot run in a user's build: it clones repositories
 * and parses XML. Its output is the bundle, which is what gets published.
 */
private fun extractBundle(rootDir: File, cldrDir: File, icuDir: File): LocaleDataBundle {
    val supplemental = parseSupplemental(cldrDir)
    val flattener = Flattener(cldrDir, supplemental)

    println("[codegen] flattening ${flattener.localeIds.size} CLDR locales")
    val dateTime = LinkedHashMap<String, String>()
    val dayPeriodGaps = LinkedHashMap<String, List<String>>()
    fun encodeChecked(id: String): String {
        val resolved = flattener.resolve(id)
        // A flexible rule type without a name renders as plain AM/PM at runtime;
        // surface how often that fallback is in play.
        val unnamed = resolved.dayPeriodRules
            .map { it.type }
            .filter { it != "am" && it != "pm" }
            .filter { resolved.dayPeriods[DAY_PERIOD_TYPES.indexOf(it) - 2].isEmpty() }
        if (unnamed.isNotEmpty()) dayPeriodGaps[id] = unnamed
        return resolved.encode()
    }
    dateTime["root"] = encodeChecked("root") // final runtime fallback
    for (id in flattener.localeIds) {
        dateTime[canonicalTag(id)] = encodeChecked(id)
    }

    val skeletonFormats = LinkedHashMap<String, String>()
    val skeletonAppendFormats = LinkedHashMap<String, String>()
    val skeletonNames = LinkedHashMap<String, String>()
    val intervalFormats = LinkedHashMap<String, String>()
    val personNames = LinkedHashMap<String, String>()
    val personNameCache = HashMap<String, PartialPersonNames>()
    fun personNamesFor(level: String): PartialPersonNames = personNameCache.getOrPut(level) {
        parsePersonNames(cldrDir.resolve("common/main/$level.xml"))
    }
    val resolvedSkeletons = LinkedHashMap<String, ResolvedSkeletonData>()
    val resolvedDateTime = LinkedHashMap<String, ResolvedLocaleData>()
    val declaredFormats = LinkedHashMap<String, Map<String, String>>()
    for (id in listOf("root") + flattener.localeIds) {
        val skeletons = flattener.resolveSkeletons(id)
        val tag = canonicalTag(id)
        skeletonFormats[tag] = skeletons.encodeFormats()
        skeletonAppendFormats[tag] = skeletons.encodeAppendFormats()
        skeletonNames[tag] = skeletons.encodeNames()
        intervalFormats[tag] = flattener.resolveIntervals(id).encode()
        personNames[tag] = flattener.resolvePersonNames(id, ::personNamesFor).encode()
        if (id != "root") {
            resolvedSkeletons[id] = skeletons
            resolvedDateTime[id] = flattener.resolve(id)
            declaredFormats[id] = flattener.declaredAvailableFormats(id)
        }
    }
    if (dayPeriodGaps.isNotEmpty()) {
        println(
            "[codegen] ${dayPeriodGaps.size} locales have day period rules without names " +
                "(am/pm fallback), e.g. ${dayPeriodGaps.entries.take(5).joinToString { "${it.key}=${it.value}" }}",
        )
    }

    crossCheckIcuVersion()

    val iso4217 = parseIso4217()
    crossCheckCurrencyNumericCodes(iso4217, icuDir)

    val territoryCodes = countryTerritoryCodes(parseRegularRegions(cldrDir), supplemental)
    val countryCodes = territoryCodes.map(TerritoryCodes::alpha2).toSet()
    val currencyEntries = buildCurrencyEntries(iso4217, parseIso4217Historic(), supplemental)
    // The whole entry set, not just the active half. CLDR carries display names
    // and symbols for the withdrawn codes too, and an old settlement record has
    // to render in the reader's language rather than as a bare code.
    val currencyCodes = currencyEntries.mapTo(HashSet(), CurrencyEntry::code)

    println("[codegen] extracting country/currency data for ${flattener.localeIds.size} CLDR locales")
    val extras = ExtrasResolver(cldrDir, flattener, supplemental, countryCodes, currencyCodes)
    val countryList = buildCountryList(territoryCodes) { alpha2 ->
        extras.resolveValue("en") { it.territoryNames[alpha2] }
    }

    val relativeCache = HashMap<String, PartialRelativeTime>()
    fun relativeFor(level: String): PartialRelativeTime = relativeCache.getOrPut(level) {
        parseRelativeTime(cldrDir.resolve("common/main/$level.xml"))
    }
    val relativeTime = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        relativeTime[canonicalTag(id)] = flattener.resolveRelativeTime(id, ::relativeFor).encode()
    }

    val durationUnitCache = HashMap<String, PartialDurationUnits>()
    fun durationUnitsFor(level: String): PartialDurationUnits = durationUnitCache.getOrPut(level) {
        parseDurationUnits(cldrDir.resolve("common/main/$level.xml"))
    }
    val durationUnits = LinkedHashMap<String, String>()
    // Root carries English rather than CLDR's root.xml, which declares only a
    // short block of placeholders. A locale with no unit wording of its own
    // resolves here, and ICU answers the same way for the same locales; see
    // resolveDurationUnits.
    durationUnits["root"] = requireNotNull(flattener.resolveDurationUnits("en", ::durationUnitsFor)) {
        "en declares no duration units, so the root fallback would be empty"
    }.encode()
    for (id in flattener.localeIds) {
        val resolved = flattener.resolveDurationUnits(id, ::durationUnitsFor) ?: continue
        durationUnits[canonicalTag(id)] = resolved.encode()
    }

    // After the extras resolver, because the capitalization bits ride along with
    // the stand-alone names and come from the same files.
    val dateTimeStandalone = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        dateTimeStandalone[canonicalTag(id)] =
            flattener.resolve(id).encodeStandalone(extras.resolveCapitalization(id))
    }

    val zoneCache = HashMap<String, PartialTimeZoneNames>()
    fun zonesFor(level: String): PartialTimeZoneNames = zoneCache.getOrPut(level) {
        parseTimeZoneNames(cldrDir.resolve("common/main/$level.xml"))
    }
    val timeZoneFormats = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        timeZoneFormats[canonicalTag(id)] = flattener.resolveTimeZoneFormats(id, ::zonesFor)
    }

    val numberSymbols = buildNumberSymbolPayloads(flattener, extras)
    val numberPatterns = buildNumberPatternPayloads(flattener, extras)
    val numberCompactShort = buildCompactPayloads(flattener, extras) { it.compactShort }
    val numberCompactLong = buildCompactPayloads(flattener, extras, fallback = { it.compactShort }) { it.compactLong }

    val timeZoneNames = buildTimeZoneNamePayloads(flattener, ::zonesFor)

    val phoneDir = ensureCloned(rootDir, PHONE_REPO)
    crossCheckPhoneVersion(phoneDir)
    val phoneMetadata = parsePhoneMetadata(phoneDir)
    emitPhoneEdgeGolden(
        outputFile = phoneConformanceDir(rootDir).resolve("PhoneEdgeGoldenData.kt"),
        tag = PHONE_REPO.tag,
        cases = extractPhoneEdgeGolden(phoneDir, phoneMetadata),
    )
    emitPhoneGolden(
        outputFile = phoneConformanceDir(rootDir).resolve("PhoneGoldenData.kt"),
        tag = PHONE_REPO.tag,
        entries = extractPhoneGolden(),
    )

    val plurals = parsePlurals(cldrDir)
    val rbnf = parseRbnfOrdinals(cldrDir, flattener.localeIds, supplemental.parentOverrides)
    emitCldrPluralSamples(
        outputFile = numberConformanceDir(rootDir).resolve("CldrPluralSampleData.kt"),
        cldrTag = CLDR_REPO.tag,
        samples = plurals.samples,
    )

    val emoji = crossCheckCountryFlags(countryList)
    emitEmojiFlagGolden(countryConformanceDir(rootDir).resolve("EmojiFlagGoldenData.kt"), emoji)

    val countryCurrencyCodes = LinkedHashMap<String, String>()
    for (country in countryList) {
        val codes = supplemental.regionCurrencies[country.alpha2]?.filter { it in currencyCodes }.orEmpty()
        if (codes.isNotEmpty()) countryCurrencyCodes[country.alpha2] = codes.joinToString(" ")
    }

    emitIcuGolden(
        outputFile = weekConformanceDir(rootDir).resolve("IcuGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuGolden(icuDir),
    )
    emitIcuCountryGolden(
        outputFile = countryConformanceDir(rootDir).resolve("IcuCountryGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuCountryGolden(icuDir),
    )
    emitCldrDateTimeCases(
        outputFile = skeletonConformanceDir(rootDir).resolve("CldrDateTimeCaseData.kt"),
        cldrTag = CLDR_REPO.tag,
        cases = extractCldrDateTimeCases(cldrDir),
    )
    val goldenSkeletons = goldenSkeletons(skeletonFormats)
    emitIcuSkeletonGolden(
        outputDir = skeletonConformanceDir(rootDir),
        icuTag = ICU_REPO.tag,
        skeletons = goldenSkeletons,
        entries = extractIcuSkeletonGolden(icuDir, goldenSkeletons, resolvedSkeletons, resolvedDateTime, declaredFormats),
    )
    emitIcuPluralGolden(
        outputFile = numberConformanceDir(rootDir).resolve("IcuPluralGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuPluralGolden(plurals.index.keys.map(::canonicalTag).toSet()),
    )
    emitIcuTimeZoneGolden(
        outputFile = timeZoneConformanceDir(rootDir).resolve("IcuTimeZoneGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuTimeZoneGolden(timeZoneNames.keys),
    )
    emitGraphemeBreakCases(
        outputFile = coreConformanceDir(rootDir).resolve("GraphemeBreakCaseData.kt"),
        ucdVersion = UCD_VERSION,
        cases = parseGraphemeBreakCases(cldrDir),
        table = encodeGraphemeBreakRanges(parseGraphemeBreakRanges()),
    )
    emitPersonNameCases(
        outputFile = personNameConformanceDir(rootDir).resolve("PersonNameCaseData.kt"),
        cldrTag = CLDR_REPO.tag,
        cases = parsePersonNameCases(cldrDir),
    )
    emitIcuIntervalGolden(
        outputFile = intervalConformanceDir(rootDir).resolve("IcuIntervalGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuIntervalGolden(intervalFormats.keys),
    )
    emitIcuWeekDataGolden(
        outputFile = weekConformanceDir(rootDir).resolve("IcuWeekDataGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuWeekDataGolden(relativeTime.keys),
    )
    emitIcuDurationUnitGolden(
        outputFile = durationUnitConformanceDir(rootDir).resolve("IcuDurationUnitGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuDurationUnitGolden(),
    )
    emitIcuNumberGolden(
        outputFile = numberConformanceDir(rootDir).resolve("IcuNumberGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuNumberGolden(icuDir, numberSymbols, numberPatterns, numberCompactShort, numberCompactLong),
    )
    emitIcuCurrencyGolden(
        outputFile = currencyConformanceDir(rootDir).resolve("IcuCurrencyGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuCurrencyGolden(icuDir),
        numericCodes = extractIcuNumericCodes(icuDir),
    )
    emitIcuCurrencyFormatGolden(
        outputFile = currencyConformanceDir(rootDir).resolve("IcuCurrencyFormatGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuCurrencyFormatGolden(currencyEntries.associate { it.code to it.minorUnits }),
    )
    emitIcuCurrencyPluralGolden(
        outputFile = currencyPluralConformanceDir(rootDir).resolve("IcuCurrencyPluralGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuCurrencyPluralGolden(currencyEntries.associate { it.code to it.minorUnits }),
    )

    val phoneTerritoryTable = encodePhoneTerritories(phoneMetadata)
    val phoneFormatTable = encodePhoneFormats(phoneMetadata)
    val phoneRoot = rootDir.sourceRoot("kotlinx-locale-phone-metadata-full")
    emitPhoneTables(
        outputRoot = phoneRoot,
        registryPackage = "dev.carcara.kotlinx.locale.phone.metadata.internal.data",
        source = "libphonenumber ${PHONE_REPO.tag}",
        territories = phoneTerritoryTable,
        formats = phoneFormatTable,
    )
    emitPhoneBinding(
        outputRoot = phoneRoot,
        spec = BindingSpec(
            packageName = "dev.carcara.kotlinx.locale.phone.metadata",
            objectName = "PhoneNumbers",
            registryPackage = "dev.carcara.kotlinx.locale.phone.metadata.internal.data",
            source = "libphonenumber ${PHONE_REPO.tag}",
        ),
        hasFormats = true,
    )

    return LocaleDataBundle.Builder()
        .apply {
            cldrVersion = CLDR_REPO.tag
            isoPublished = iso4217.published
            localeTags = flattener.localeIds.map(::canonicalTag)
            countries = countryList
            currencies = currencyEntries
            countryCurrencies = countryCurrencyCodes
        }
        .section("dateTime", dateTime)
        .section("dateTimeStandalone", dateTimeStandalone)
        .section("localeDisplayNames", buildLocaleDisplayNamePayloads(flattener, extras))
        .section("relativeTime", relativeTime)
        .section("durationUnits", durationUnits)
        .section("timeZoneFormats", timeZoneFormats)
        .section("timeZoneNames", timeZoneNames)
        .section("timeZoneCities", buildTimeZoneCityPayloads(flattener, ::zonesFor))
        .table(BundleTables.TIME_ZONE_METADATA, encodeTimeZoneMetadata(cldrDir))
        .table(BundleTables.PHONE_TERRITORIES, phoneTerritoryTable)
        .table(BundleTables.PHONE_FORMATS, phoneFormatTable)
        .table(BundleTables.WEEK_DATA, supplemental.encodeWeekData())
        .table(BundleTables.GRAPHEME_BREAK, encodeGraphemeBreakRanges(parseGraphemeBreakRanges()))
        .table(BundleTables.WORD_BREAK_MID, parseWordBreakMidLetters())
        .section("countryNames", buildCountryNamePayloads(flattener, extras))
        .section("currencyFormats", buildCurrencyFormatPayloads(flattener, extras))
        .section("currencyNames", buildCurrencyNamePayloads(flattener, extras))
        .section("currencyPluralNames", buildCurrencyPluralNamePayloads(flattener, extras))
        .section("skeletonFormats", skeletonFormats)
        .section("skeletonAppendFormats", skeletonAppendFormats)
        .section("skeletonNames", skeletonNames)
        .section("intervalFormats", intervalFormats)
        .section("personNames", personNames)
        .section("numberSymbols", numberSymbols)
        .section("numberPatterns", numberPatterns)
        .section("numberCompactShort", numberCompactShort)
        .section("numberCompactLong", numberCompactLong)
        .section("currencyCompactShort", buildCompactPayloads(flattener, extras) { it.currencyCompact })
        .section("pluralRuleSets", plurals.ruleSets)
        .section("pluralRuleIndex", plurals.index.mapKeys(::canonicalTagOf))
        .section("ordinalRuleSets", rbnf.closures)
        .section("ordinalRuleIndex", rbnf.index.mapKeys(::canonicalTagOf))
        .build()
}

/** A map key that is a CLDR locale id, as the canonical tag the runtime looks up by. */
private fun canonicalTagOf(entry: Map.Entry<String, String>): String = canonicalTag(entry.key)
