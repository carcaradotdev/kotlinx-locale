package dev.carcara.kotlinx.locale.codegen

import java.io.File

fun main(args: Array<String>) {
    val mode = args.getOrNull(0) ?: "generate"
    val rootDir = File(args.getOrNull(1) ?: ".").absoluteFile

    when (mode) {
        "clone" -> {
            ensureCloned(rootDir, CLDR_REPO)
            ensureCloned(rootDir, ICU_REPO)
        }
        "generate" -> {
            val cldrDir = ensureCloned(rootDir, CLDR_REPO)
            val icuDir = ensureCloned(rootDir, ICU_REPO)
            generate(rootDir, cldrDir, icuDir)
        }
        else -> error("Unknown mode '$mode'. Use 'clone' or 'generate'.")
    }
}

/** `<module>/src/<sourceSet>/kotlin`, the package root the emitters write under. */
private fun File.sourceRoot(module: String, sourceSet: String = "commonMain"): File = resolve("$module/src/$sourceSet/kotlin")

/**
 * The ICU fixtures live in the conformance module rather than in one domain's
 * tests, so that any source can be checked against them and not just the
 * bundled one.
 */
private fun conformanceDir(rootDir: File): File = rootDir
    .sourceRoot("conformance-test-suite")
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
    .table(GeneratedTable.COUNTRY_NAMES, rootDir.sourceRoot("kotlinx-locale-country-cldr-full"))
    .table(GeneratedTable.CURRENCY_ENUM, rootDir.sourceRoot("kotlinx-locale-currency-types"))
    .table(GeneratedTable.COUNTRY_CURRENCIES, rootDir.sourceRoot("kotlinx-locale-currency-types"))
    .table(GeneratedTable.CURRENCY_FORMATS, rootDir.sourceRoot("kotlinx-locale-currency-cldr-full"))
    .table(GeneratedTable.CURRENCY_NAMES, rootDir.sourceRoot("kotlinx-locale-currency-cldr-full"))
    .table(GeneratedTable.DATE_TIME, rootDir.sourceRoot("kotlinx-locale-datetime-cldr-full"))
    .table(GeneratedTable.SKELETONS, rootDir.sourceRoot("kotlinx-locale-datetime-cldr-skeletons"))
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
    val resolvedSkeletons = LinkedHashMap<String, ResolvedSkeletonData>()
    val resolvedDateTime = LinkedHashMap<String, ResolvedLocaleData>()
    val declaredFormats = LinkedHashMap<String, Map<String, String>>()
    for (id in listOf("root") + flattener.localeIds) {
        val skeletons = flattener.resolveSkeletons(id)
        val tag = canonicalTag(id)
        skeletonFormats[tag] = skeletons.encodeFormats()
        skeletonAppendFormats[tag] = skeletons.encodeAppendFormats()
        skeletonNames[tag] = skeletons.encodeNames()
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

    val iso4217 = parseIso4217()
    crossCheckCurrencyNumericCodes(iso4217, icuDir)

    val territoryCodes = countryTerritoryCodes(parseRegularRegions(cldrDir), supplemental)
    val countryCodes = territoryCodes.map(TerritoryCodes::alpha2).toSet()
    val currencyCodes = iso4217.currencies.map(Iso4217Currency::code).toSet()

    println("[codegen] extracting country/currency data for ${flattener.localeIds.size} CLDR locales")
    val extras = ExtrasResolver(cldrDir, flattener, supplemental, countryCodes, currencyCodes)
    val countryList = buildCountryList(territoryCodes) { alpha2 ->
        extras.resolveValue("en") { it.territoryNames[alpha2] }
    }

    val emoji = crossCheckCountryFlags(countryList)
    emitEmojiFlagGolden(conformanceDir(rootDir).resolve("EmojiFlagGoldenData.kt"), emoji)

    val countryCurrencyCodes = LinkedHashMap<String, String>()
    for (country in countryList) {
        val codes = supplemental.regionCurrencies[country.alpha2]?.filter { it in currencyCodes }.orEmpty()
        if (codes.isNotEmpty()) countryCurrencyCodes[country.alpha2] = codes.joinToString(" ")
    }

    emitIcuGolden(
        outputFile = conformanceDir(rootDir).resolve("IcuGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuGolden(icuDir),
    )
    emitIcuCountryGolden(
        outputFile = conformanceDir(rootDir).resolve("IcuCountryGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuCountryGolden(icuDir),
    )
    emitCldrDateTimeCases(
        outputFile = conformanceDir(rootDir).resolve("CldrDateTimeCaseData.kt"),
        cldrTag = CLDR_REPO.tag,
        cases = extractCldrDateTimeCases(cldrDir),
    )
    val goldenSkeletons = goldenSkeletons(skeletonFormats)
    emitIcuSkeletonGolden(
        outputDir = conformanceDir(rootDir),
        icuTag = ICU_REPO.tag,
        skeletons = goldenSkeletons,
        entries = extractIcuSkeletonGolden(icuDir, goldenSkeletons, resolvedSkeletons, resolvedDateTime, declaredFormats),
    )
    emitIcuCurrencyGolden(
        outputFile = conformanceDir(rootDir).resolve("IcuCurrencyGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuCurrencyGolden(icuDir),
        numericCodes = extractIcuNumericCodes(icuDir),
    )

    return LocaleDataBundle.Builder()
        .apply {
            cldrVersion = CLDR_REPO.tag
            isoPublished = iso4217.published
            localeTags = flattener.localeIds.map(::canonicalTag)
            countries = countryList
            currencies = iso4217.currencies.map { iso ->
                val fractions = supplemental.currencyFractions[iso.code] ?: supplemental.defaultFractions
                CurrencyEntry(
                    code = iso.code,
                    numericCode = iso.numericCode ?: -1,
                    minorUnits = iso.minorUnits,
                    cldrDigits = fractions.digits,
                    cldrRounding = fractions.rounding,
                    cldrCashDigits = fractions.cashDigits,
                    cldrCashRounding = fractions.cashRounding,
                    englishName = iso.name,
                )
            }
            countryCurrencies = countryCurrencyCodes
        }
        .section("dateTime", dateTime)
        .section("countryNames", buildCountryNamePayloads(flattener, extras))
        .section("currencyFormats", buildCurrencyFormatPayloads(flattener, extras))
        .section("currencyNames", buildCurrencyNamePayloads(flattener, extras))
        .section("skeletonFormats", skeletonFormats)
        .section("skeletonAppendFormats", skeletonAppendFormats)
        .section("skeletonNames", skeletonNames)
        .build()
}
