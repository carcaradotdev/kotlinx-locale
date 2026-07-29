package dev.carcara.kotlinx.locale.codegen

import java.io.File

/**
 * Where each piece of generated source goes.
 *
 * A null root means "do not generate this", which is how the Gradle plugin
 * turns features off and how a caller that only wants data skips the enums.
 * Every root is a package root: the emitters append their own package path.
 */
public class SourceRoots(
    public val localeCatalog: File? = null,
    public val countryEnum: File? = null,
    public val countryNames: File? = null,
    public val currencyEnum: File? = null,
    public val countryCurrencies: File? = null,
    public val currencyFormats: File? = null,
    public val currencyNames: File? = null,
    public val dateTime: File? = null,
)

/**
 * The package the payload registries are written into.
 *
 * The shipped modules use the library's own, so their registries stay internal
 * to the artifact that owns them. A build generating its own narrowed data uses
 * its own, so the two can sit on one classpath.
 */
public class RegistryPackages(
    public val countryNames: String,
    public val currencyFormats: String,
    public val currencyNames: String,
    public val dateTime: String,
) {
    public companion object {

        /** What the published `-cldr` artifacts use. */
        public val SHIPPED: RegistryPackages = RegistryPackages(
            countryNames = "dev.carcara.kotlinx.locale.country.cldr.internal.data",
            currencyFormats = "dev.carcara.kotlinx.locale.currency.cldr.internal.data",
            currencyNames = "dev.carcara.kotlinx.locale.currency.cldr.internal.data",
            dateTime = "dev.carcara.kotlinx.locale.datetime.cldr.internal.data",
        )

        /** Everything under one package, which is what a generated source set wants. */
        public fun under(basePackage: String): RegistryPackages = RegistryPackages(
            countryNames = "$basePackage.internal.data",
            currencyFormats = "$basePackage.internal.data",
            currencyNames = "$basePackage.internal.data",
            dateTime = "$basePackage.internal.data",
        )
    }
}

/**
 * Writes Kotlin sources for everything [roots] asks for.
 *
 * This is the single code path the shipped artifacts and the Gradle plugin both
 * run. They differ in which roots they pass and which package the registries
 * land in, never in what is written into them, which is what keeps the two from
 * drifting. `BundleRoundTripTest` pins that down by regenerating the shipped
 * sources from the published bundle and comparing byte for byte.
 */
public fun generateSources(bundle: LocaleDataBundle, roots: SourceRoots, packages: RegistryPackages = RegistryPackages.SHIPPED) {
    val cldr = bundle.cldrVersion

    roots.localeCatalog?.let { root ->
        emitLocaleCatalog(
            outputDir = root.packageDir("dev.carcara.kotlinx.locale.catalog"),
            cldrTag = cldr,
            localeTags = bundle.localeTags,
        )
    }

    roots.countryEnum?.let { root ->
        emitCountryEnum(
            outputFile = root.packageDir("dev.carcara.kotlinx.locale.country").resolve("Country.kt"),
            cldrTag = cldr,
            countries = bundle.countries,
        )
    }

    roots.currencyEnum?.let { root ->
        emitCurrencyEnum(
            outputFile = root.packageDir("dev.carcara.kotlinx.locale.currency").resolve("Currency.kt"),
            cldrTag = cldr,
            isoPublished = bundle.isoPublished,
            currencies = bundle.currencies,
        )
    }

    roots.countryCurrencies?.let { root ->
        emitCountryCurrencies(
            outputFile = root.packageDir("dev.carcara.kotlinx.locale.currency.internal")
                .resolve("CountryCurrencies.kt"),
            cldrTag = cldr,
            mapping = bundle.countryCurrencies,
        )
    }

    roots.countryNames?.let { root ->
        KeyedPayloadEmitter(
            outputDir = root.packageDir(packages.countryNames),
            packageName = packages.countryNames,
            filePrefix = "CountryNames",
            constPrefix = "COUNTRY_NAMES",
            registryProperty = "countryNamesRegistry",
            source = "CLDR $cldr",
            versionConst = "CLDR_VERSION" to cldr,
        ).emit(bundle.countryNames)
    }

    roots.currencyFormats?.let { root ->
        KeyedPayloadEmitter(
            outputDir = root.packageDir(packages.currencyFormats),
            packageName = packages.currencyFormats,
            filePrefix = "CurrencyFormats",
            constPrefix = "CURRENCY_FORMATS",
            registryProperty = "currencyFormatsRegistry",
            source = "CLDR $cldr",
            versionConst = "CLDR_VERSION" to cldr,
        ).emit(bundle.currencyFormats)
    }

    roots.currencyNames?.let { root ->
        KeyedPayloadEmitter(
            outputDir = root.packageDir(packages.currencyNames),
            packageName = packages.currencyNames,
            filePrefix = "CurrencyNames",
            constPrefix = "CURRENCY_NAMES",
            registryProperty = "currencyNamesRegistry",
            source = "CLDR $cldr",
        ).emit(bundle.currencyNames)
    }

    roots.dateTime?.let { root ->
        LocaleDataEmitter(
            outputDir = root.packageDir(packages.dateTime),
            cldrTag = cldr,
            packageName = packages.dateTime,
        ).emit(bundle.dateTime)
    }
}

private fun File.packageDir(packageName: String): File = resolve(packageName.replace('.', '/'))
