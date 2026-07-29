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
    .sourceRoot("conformance")
    .resolve("dev/carcara/kotlinx/locale/conformance")

/** Where the published bundle lives, as a resource inside kotlinx-locale-cldr-data. */
internal fun bundleFile(rootDir: File): File = rootDir.resolve("cldr-data/src/main/resources/dev/carcara/kotlinx/locale/cldr-data.txt")

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
internal fun shippedRoots(rootDir: File): SourceRoots = SourceRoots(
    localeCatalog = rootDir.sourceRoot("locale-types"),
    countryEnum = rootDir.sourceRoot("country-types"),
    countryNames = rootDir.sourceRoot("country-cldr"),
    currencyEnum = rootDir.sourceRoot("currency-types"),
    countryCurrencies = rootDir.sourceRoot("currency-types"),
    currencyFormats = rootDir.sourceRoot("currency-cldr"),
    currencyNames = rootDir.sourceRoot("currency-cldr"),
    dateTime = rootDir.sourceRoot("datetime-cldr"),
)

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
    val countries = buildCountryList(territoryCodes) { alpha2 ->
        extras.resolveValue("en") { it.territoryNames[alpha2] }
    }

    val countryCurrencies = LinkedHashMap<String, String>()
    for (country in countries) {
        val codes = supplemental.regionCurrencies[country.alpha2]?.filter { it in currencyCodes }.orEmpty()
        if (codes.isNotEmpty()) countryCurrencies[country.alpha2] = codes.joinToString(" ")
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
    emitIcuCurrencyGolden(
        outputFile = conformanceDir(rootDir).resolve("IcuCurrencyGoldenData.kt"),
        icuTag = ICU_REPO.tag,
        entries = extractIcuCurrencyGolden(icuDir),
        numericCodes = extractIcuNumericCodes(icuDir),
    )

    return LocaleDataBundle(
        cldrVersion = CLDR_REPO.tag,
        isoPublished = iso4217.published,
        localeTags = flattener.localeIds.map(::canonicalTag),
        countries = countries,
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
        },
        countryCurrencies = countryCurrencies,
        dateTime = dateTime,
        countryNames = buildCountryNamePayloads(flattener, extras),
        currencyFormats = buildCurrencyFormatPayloads(flattener, extras),
        currencyNames = buildCurrencyNamePayloads(flattener, extras),
    )
}
