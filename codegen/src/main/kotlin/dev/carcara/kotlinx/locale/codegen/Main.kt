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

/** `<module>/src/<sourceSet>/kotlin/<package as path>`. */
private fun File.sourceDir(module: String, sourceSet: String, packageName: String): File =
    resolve("$module/src/$sourceSet/kotlin/${packageName.replace('.', '/')}")

private fun generate(rootDir: File, cldrDir: File, icuDir: File) {
    val supplemental = parseSupplemental(cldrDir)
    val flattener = Flattener(cldrDir, supplemental)

    println("[codegen] flattening ${flattener.localeIds.size} CLDR locales")
    val encoded = LinkedHashMap<String, String>()
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
    encoded["root"] = encodeChecked("root") // final runtime fallback
    for (id in flattener.localeIds) {
        encoded[id] = encodeChecked(id)
    }
    if (dayPeriodGaps.isNotEmpty()) {
        println(
            "[codegen] ${dayPeriodGaps.size} locales have day period rules without names " +
                "(am/pm fallback), e.g. ${dayPeriodGaps.entries.take(5).joinToString { "${it.key}=${it.value}" }}",
        )
    }

    LocaleDataEmitter(
        outputDir = rootDir.sourceDir("datetime-cldr", "commonMain", "dev.carcara.kotlinx.locale.datetime.cldr.internal.data"),
        cldrTag = CLDR_REPO.tag,
    ).emit(encoded)

    emitLocaleCatalog(
        outputDir = rootDir.sourceDir("locale-types", "commonMain", "dev.carcara.kotlinx.locale.catalog"),
        cldrTag = CLDR_REPO.tag,
        localeIds = flattener.localeIds,
    )

    emitIcuGolden(
        outputFile = rootDir
            .sourceDir("datetime-cldr", "commonTest", "dev.carcara.kotlinx.locale.datetime")
            .resolve("IcuGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuGolden(icuDir),
    )

    generateCountryAndCurrency(rootDir, cldrDir, icuDir, supplemental, flattener)

    println("[codegen] done")
}

private fun generateCountryAndCurrency(rootDir: File, cldrDir: File, icuDir: File, supplemental: SupplementalData, flattener: Flattener) {
    val iso4217 = parseIso4217()
    crossCheckCurrencyNumericCodes(iso4217, icuDir)

    val territoryCodes = countryTerritoryCodes(parseRegularRegions(cldrDir), supplemental)
    val countryCodes = territoryCodes.map(TerritoryCodes::alpha2).toSet()
    val currencyCodes = iso4217.currencies.map(Iso4217Currency::code).toSet()

    println("[codegen] extracting country/currency data for ${flattener.localeIds.size} CLDR locales")
    val extras = ExtrasResolver(cldrDir, flattener, supplemental, countryCodes, currencyCodes)
    val countries = buildCountryList(territoryCodes) { alpha2 ->
        extras.resolveValue("en") { it.territoryNames[alpha2] }
    }

    emitCountryEnum(
        outputFile = rootDir
            .sourceDir("country-types", "commonMain", "dev.carcara.kotlinx.locale.country")
            .resolve("Country.kt"),
        cldrTag = CLDR_REPO.tag,
        countries = countries,
    )
    KeyedPayloadEmitter(
        outputDir = rootDir.sourceDir("country-cldr", "commonMain", "dev.carcara.kotlinx.locale.country.cldr.internal.data"),
        packageName = "dev.carcara.kotlinx.locale.country.cldr.internal.data",
        filePrefix = "CountryNames",
        constPrefix = "COUNTRY_NAMES",
        registryProperty = "countryNamesRegistry",
        source = "CLDR ${CLDR_REPO.tag}",
        versionConst = "CLDR_VERSION" to CLDR_REPO.tag,
    ).emit(buildCountryNamePayloads(flattener, extras))

    val currencies = iso4217.currencies.map { iso ->
        CurrencyGen(iso, supplemental.currencyFractions[iso.code] ?: supplemental.defaultFractions)
    }
    emitCurrencyEnum(
        outputFile = rootDir
            .sourceDir("currency-types", "commonMain", "dev.carcara.kotlinx.locale.currency")
            .resolve("Currency.kt"),
        cldrTag = CLDR_REPO.tag,
        isoPublished = iso4217.published,
        currencies = currencies,
    )
    KeyedPayloadEmitter(
        outputDir = rootDir.sourceDir("currency-cldr", "commonMain", "dev.carcara.kotlinx.locale.currency.cldr.internal.data"),
        packageName = "dev.carcara.kotlinx.locale.currency.cldr.internal.data",
        filePrefix = "CurrencyFormats",
        constPrefix = "CURRENCY_FORMATS",
        registryProperty = "currencyFormatsRegistry",
        source = "CLDR ${CLDR_REPO.tag}",
        versionConst = "CLDR_VERSION" to CLDR_REPO.tag,
    ).emit(buildCurrencyFormatPayloads(flattener, extras))
    KeyedPayloadEmitter(
        outputDir = rootDir.sourceDir("currency-cldr", "commonMain", "dev.carcara.kotlinx.locale.currency.cldr.internal.data"),
        packageName = "dev.carcara.kotlinx.locale.currency.cldr.internal.data",
        filePrefix = "CurrencyNames",
        constPrefix = "CURRENCY_NAMES",
        registryProperty = "currencyNamesRegistry",
        source = "CLDR ${CLDR_REPO.tag}",
    ).emit(buildCurrencyNamePayloads(flattener, extras))
    emitCountryCurrencies(
        outputFile = rootDir
            .sourceDir("currency-types", "commonMain", "dev.carcara.kotlinx.locale.currency.internal")
            .resolve("CountryCurrencies.kt"),
        cldrTag = CLDR_REPO.tag,
        countries = countries,
        supplemental = supplemental,
        currencyCodes = currencyCodes,
    )

    emitIcuCountryGolden(
        outputFile = rootDir
            .sourceDir("country-cldr", "commonTest", "dev.carcara.kotlinx.locale.country")
            .resolve("IcuCountryGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuCountryGolden(icuDir),
    )
    emitIcuCurrencyGolden(
        outputFile = rootDir
            .sourceDir("currency-cldr", "commonTest", "dev.carcara.kotlinx.locale.currency")
            .resolve("IcuCurrencyGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuCurrencyGolden(icuDir),
        numericCodes = extractIcuNumericCodes(icuDir),
    )
}
