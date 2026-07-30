package dev.carcara.kotlinx.locale.codegen

import java.io.File

/**
 * Where each piece of generated source goes.
 *
 * A null root means "do not generate this", which is how the Gradle plugin
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
)

public class SourceRoots(
    public val localeCatalog: File? = null,
    public val countryEnum: File? = null,
    public val countryNames: File? = null,
    public val currencyEnum: File? = null,
    public val countryCurrencies: File? = null,
    public val currencyFormats: File? = null,
    public val currencyNames: File? = null,
    public val dateTime: File? = null,
    /** The three skeleton tables, which travel together. */
    public val skeletons: File? = null,
    /** The source object and convenience extensions over the country table. */
    public val countryBinding: BindingTarget? = null,
    public val currencyBinding: BindingTarget? = null,
    public val dateTimeBinding: BindingTarget? = null,
    /**
     * The skeleton source object. Needs [dateTimeBinding] too: a skeleton
     * binding reads the pattern table through it rather than carrying a copy.
     */
    public val skeletonBinding: BindingTarget? = null,
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
    public val skeletons: String,
) {
    public companion object {

        /** What the published `-cldr-full` artifacts use. */
        public val SHIPPED: RegistryPackages = RegistryPackages(
            countryNames = "dev.carcara.kotlinx.locale.country.cldr.internal.data",
            currencyFormats = "dev.carcara.kotlinx.locale.currency.cldr.internal.data",
            currencyNames = "dev.carcara.kotlinx.locale.currency.cldr.internal.data",
            dateTime = "dev.carcara.kotlinx.locale.datetime.cldr.internal.data",
            skeletons = "dev.carcara.kotlinx.locale.datetime.cldr.skeletons.internal.data",
        )

        /** Everything under one package, which is what a generated source set wants. */
        public fun under(basePackage: String): RegistryPackages = RegistryPackages(
            countryNames = "$basePackage.internal.data",
            currencyFormats = "$basePackage.internal.data",
            currencyNames = "$basePackage.internal.data",
            dateTime = "$basePackage.internal.data",
            skeletons = "$basePackage.internal.data",
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
            versionConst = "COUNTRY_NAMES_CLDR_VERSION" to cldr,
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
            versionConst = "CURRENCY_FORMATS_CLDR_VERSION" to cldr,
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

    roots.skeletons?.let { root ->
        // Three tables rather than one record: a locale's own availableFormats
        // is the bulk of it, its append formats are almost always root's, and
        // its names sit somewhere between. Deduplicating them together would
        // cost the two small ones the saving they have.
        fun emitSkeletonTable(payloads: Map<String, String>, filePrefix: String, constPrefix: String, property: String) {
            KeyedPayloadEmitter(
                outputDir = root.packageDir(packages.skeletons),
                packageName = packages.skeletons,
                filePrefix = filePrefix,
                constPrefix = constPrefix,
                registryProperty = property,
                source = "CLDR $cldr",
            ).emit(payloads)
        }
        emitSkeletonTable(bundle.skeletonFormats, "SkeletonFormats", "SKELETON_FORMATS", "skeletonFormatsRegistry")
        emitSkeletonTable(
            bundle.skeletonAppendFormats,
            "SkeletonAppendFormats",
            "SKELETON_APPEND_FORMATS",
            "skeletonAppendFormatsRegistry",
        )
        emitSkeletonTable(bundle.skeletonNames, "SkeletonNames", "SKELETON_NAMES", "skeletonNamesRegistry")
    }

    roots.countryBinding?.let { target ->
        emitCountryBinding(target.root, target.spec(packages.countryNames, cldr))
    }

    roots.currencyBinding?.let { target ->
        emitCurrencyBinding(target.root, target.spec(packages.currencyNames, cldr))
    }

    roots.dateTimeBinding?.let { target ->
        emitDateTimeBinding(target.root, target.spec(packages.dateTime, cldr))
    }

    roots.skeletonBinding?.let { target ->
        val dateTime = requireNotNull(roots.dateTimeBinding) {
            "a skeleton binding reads its patterns through the datetime binding, so it needs one"
        }
        emitSkeletonBinding(
            target.root,
            target.spec(packages.skeletons, cldr),
            dateTimeObject = "${dateTime.packageName}.${dateTime.objectName}",
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
