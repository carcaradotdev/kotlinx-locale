package dev.carcara.kotlinx.locale.codegen

import java.io.BufferedWriter
import java.io.Reader

/** One ISO 3166-1 country, with the English name that becomes its KDoc. */
public class CountryInfo(public val alpha2: String, public val alpha3: String, public val numeric: Int, public val englishName: String) {
    public companion object
}

/** One ISO 4217 currency, with the CLDR fraction behavior that rides along with it. */
public class CurrencyEntry(
    public val code: String,
    /** -1 when ISO assigns none. */
    public val numericCode: Int,
    /** ISO minor units; -1 when the standard lists "N.A.". */
    public val minorUnits: Int,
    public val cldrDigits: Int,
    public val cldrRounding: Int,
    public val cldrCashDigits: Int,
    public val cldrCashRounding: Int,
    public val englishName: String,
    /**
     * The first and last day any region held this as legal tender, as proleptic
     * Gregorian day numbers, and whether it is a tender code at all.
     *
     * Unbounded ends are [Int.MIN_VALUE] and [Int.MAX_VALUE]. A code that is
     * still current has an unbounded end, which is what makes "is this active"
     * a comparison rather than a flag CLDR would have to restate every release.
     */
    public val tenderFrom: Int = Int.MIN_VALUE,
    public val tenderTo: Int = Int.MAX_VALUE,
    public val isTender: Boolean = true,
    /**
     * Whether ISO still lists this code, which is what decides "active".
     *
     * List membership rather than the CLDR window, because the two disagree and
     * ISO owns the question: CLDR closed the Salvadoran colón when El Salvador
     * adopted the dollar, while ISO still publishes SVC in list one. The window
     * says when a code was tender somewhere; this says whether the standard
     * still carries it.
     */
    public val isCurrent: Boolean = true,
) {
    public companion object
}

/**
 * Everything the emitters need, already resolved out of CLDR.
 *
 * This is the intermediate the pipeline always had: [Flattener] resolves
 * inheritance and each locale encodes to one compact string. Publishing it is
 * what lets a user's build generate a narrowed source set without cloning CLDR
 * — there is no network beyond dependency resolution, and the CLDR version is
 * visible in their lock file.
 *
 * The per-locale payloads live in [sections], keyed by the names declared in
 * [BundleSection.ALL], rather than as one constructor parameter each. A section
 * that is a constructor parameter has to be named in five places to be added and
 * carries its sparse-or-resolved nature as a literal at the [narrowTo] call
 * site; a section that is a map row is declared once, next to the number that
 * says how narrowing must treat it.
 */
public class LocaleDataBundle private constructor(
    public val cldrVersion: String,
    public val isoPublished: String,
    /** Canonical BCP 47 tags, without root. */
    public val localeTags: List<String>,
    public val countries: List<CountryInfo>,
    public val currencies: List<CurrencyEntry>,
    /** alpha-2 -> space-separated currency codes, preferred first. */
    public val countryCurrencies: Map<String, String>,
    /** Locale-independent payloads, keyed by the names in [BundleTables]. */
    public val tables: Map<String, String>,
    /** Per-locale payloads, keyed by the names in [BundleSection.ALL]. */
    public val sections: Map<String, Map<String, String>>,
) {

    /** The payloads of one section, empty when this bundle carries none. */
    public fun section(name: String): Map<String, String> = sections[name].orEmpty()

    /** Fully resolved datetime records, including `root`. */
    public val dateTime: Map<String, String> get() = section("dateTime")

    /** Sparse country-name records carrying their parent tag, including `root`. */
    public val countryNames: Map<String, String> get() = section("countryNames")

    /** Fully resolved number-format records, including `root`. */
    public val currencyFormats: Map<String, String> get() = section("currencyFormats")

    /** Sparse currency symbol and name records carrying their parent tag, including `root`. */
    public val currencyNames: Map<String, String> get() = section("currencyNames")

    /**
     * Fully resolved skeleton tables, including `root`.
     *
     * Three sections rather than one record because they dedupe on very
     * different scales: almost every locale has its own `availableFormats`,
     * almost none has its own `appendItems`.
     */
    public val skeletonFormats: Map<String, String> get() = section("skeletonFormats")

    /** Fully resolved `appendItems` patterns, including `root`. */
    public val skeletonAppendFormats: Map<String, String> get() = section("skeletonAppendFormats")

    /** Fully resolved field display names and quarter names, including `root`. */
    public val skeletonNames: Map<String, String> get() = section("skeletonNames")

    /**
     * The same bundle carrying only [tags], the locales they inherit from, and
     * root.
     *
     * Ancestors are not optional. A country-name record holds only what that
     * locale's own CLDR file declares and points at its parent for the rest, so
     * a build that kept `pt-BR` and dropped `pt` would resolve almost nothing.
     * Every sparse section is walked rather than the two that happen to exist
     * today, so a section added later cannot be forgotten here.
     *
     * A section declared `narrowed = false` is copied through whole. Those carry
     * data that does not vary by locale, where dropping rows saves nothing and
     * turns an unlisted locale into wrong output instead of an error.
     *
     * [fallbackTag], when given, becomes the record stored under `root`, which is
     * what the runtime reaches for when a locale is not in the set at all. That
     * is what keeps a narrowed source total: ask a three-locale build for `ja`
     * and it answers in the fallback locale rather than returning nothing. It
     * must be one of [tags], since a fallback nobody generated data for would not
     * answer either.
     */
    public fun narrowTo(tags: Set<String>, fallbackTag: String? = null): LocaleDataBundle {
        require(tags.isNotEmpty()) { "narrowing to no locales would generate nothing" }
        // localeTags rather than one section's keys: "does CLDR have this locale"
        // is a question about the locale list, and reading it off a section made
        // that section quietly load-bearing for every other one.
        val available = localeTags.toHashSet()
        val kept = LinkedHashSet<String>()
        for (tag in tags.sorted()) {
            require(tag in available) { "no CLDR data for locale '$tag'" }
            kept += tag
            for (declared in BundleSection.ALL) {
                if (declared.isSparse) kept += ancestorsOf(tag, section(declared.name))
            }
        }
        kept += "root"
        if (fallbackTag != null) {
            require(fallbackTag in tags) { "the fallback locale '$fallbackTag' is not one of the generated locales" }
        }

        val narrowed = LinkedHashMap<String, Map<String, String>>(sections.size)
        for (declared in BundleSection.ALL) {
            val full = section(declared.name)
            if (full.isEmpty()) continue
            narrowed[declared.name] = if (!declared.narrowed) {
                full
            } else {
                withFallback(full.filterKeys { it in kept }, full, fallbackTag, declared.sparseFields)
            }
        }

        return LocaleDataBundle(
            cldrVersion = cldrVersion,
            isoPublished = isoPublished,
            localeTags = localeTags.filter { it in kept },
            countries = countries,
            currencies = currencies,
            countryCurrencies = countryCurrencies,
            tables = tables,
            sections = narrowed,
        )
    }

    /**
     * Replaces the `root` record with the fallback locale's, so an unlisted
     * locale resolves to the fallback instead of to CLDR root.
     *
     * A resolved record ([sparseFields] of 0) can be copied as it is. A sparse
     * one cannot: it holds only what its own locale declared and defers the rest
     * to its parent, so copying `pt-BR` to `root` would resolve almost nothing.
     * The chain is flattened into one parentless record instead, nearest
     * declaration winning, which is exactly what a lookup starting at the
     * fallback would have found.
     */
    private fun withFallback(
        narrowed: Map<String, String>,
        full: Map<String, String>,
        fallbackTag: String?,
        sparseFields: Int,
    ): Map<String, String> {
        if (fallbackTag == null) return narrowed
        val record = full[fallbackTag] ?: return narrowed
        val rootRecord = if (sparseFields == 0) record else flattenSparse(full, fallbackTag, sparseFields)
        return LinkedHashMap(narrowed).apply { put("root", rootRecord) }
    }

    /** The fallback's whole chain as one parentless record with [fields] data fields. */
    private fun flattenSparse(full: Map<String, String>, fallbackTag: String, fields: Int): String {
        val merged = List(fields) { LinkedHashMap<String, String>() }
        var tag: String? = fallbackTag
        var hops = 0
        while (tag != null && hops++ < 16) {
            val record = full[tag] ?: break
            val parts = record.split(FIELD_SEPARATOR)
            for (field in 1..fields) {
                val body = parts.getOrNull(field) ?: continue
                for (entry in body.split(LIST_SEPARATOR)) {
                    if (entry.isEmpty()) continue
                    val separator = entry.indexOf(KEY_SEPARATOR)
                    if (separator <= 0) continue
                    // Nearest declaration wins, so only fill what is still absent.
                    merged[field - 1].putIfAbsent(entry.substring(0, separator), entry.substring(separator + 1))
                }
            }
            tag = parts.firstOrNull()?.takeIf(String::isNotEmpty)
        }
        return buildString {
            append("") // an empty parent field: the chain ends here
            for (field in merged) {
                append(FIELD_SEPARATOR)
                append(
                    field.entries
                        .sortedBy { it.key }
                        .joinToString(LIST_SEPARATOR) { it.key + KEY_SEPARATOR + it.value },
                )
            }
        }
    }

    /** The chain a sparse record walks: the parent tag is field 0 of the payload. */
    private fun ancestorsOf(tag: String, payloads: Map<String, String>): Set<String> {
        val chain = LinkedHashSet<String>()
        var current = tag
        var hops = 0
        while (hops++ < 16) {
            val payload = payloads[current] ?: break
            val parent = payload.substringBefore(FIELD_SEPARATOR)
            if (parent.isEmpty() || !chain.add(parent)) break
            current = parent
        }
        return chain
    }

    public fun writeTo(out: BufferedWriter) {
        fun section(name: String) {
            out.write("#$name\n")
        }
        fun row(vararg fields: String) {
            for (field in fields) {
                require('\t' !in field && '\n' !in field) { "a bundle field cannot contain a tab or newline: '$field'" }
            }
            out.write(fields.joinToString("\t"))
            out.write("\n")
        }

        section("bundle $FORMAT_VERSION")
        row("cldr", cldrVersion)
        row("iso4217", isoPublished)

        section("countries")
        for (country in countries) row(country.alpha2, country.alpha3, country.numeric.toString(), country.englishName)

        section("currencies")
        for (currency in currencies) {
            row(
                currency.code,
                currency.numericCode.toString(),
                currency.minorUnits.toString(),
                currency.cldrDigits.toString(),
                currency.cldrRounding.toString(),
                currency.cldrCashDigits.toString(),
                currency.cldrCashRounding.toString(),
                currency.englishName,
                currency.tenderFrom.toString(),
                currency.tenderTo.toString(),
                if (currency.isTender) "1" else "0",
                if (currency.isCurrent) "1" else "0",
            )
        }

        section("countryCurrencies")
        for ((alpha2, codes) in countryCurrencies) row(alpha2, codes)

        section("localeTags")
        for (tag in localeTags) row(tag)

        for (name in BundleTables.ALL) {
            val payload = tables[name] ?: continue
            section(name)
            row(payload)
        }

        for (declared in BundleSection.ALL) {
            val payloads = sections[declared.name] ?: continue
            section(declared.name)
            for ((tag, payload) in payloads) row(tag, payload)
        }

        section("end")
        out.flush()
    }

    /**
     * Collects the parts of a bundle before checking them.
     *
     * A builder rather than a constructor because the parts arrive from
     * different places: the locale-keyed sections come out of the flattener one
     * at a time, the entity lists out of ISO and CLDR validity data. A
     * constructor with one parameter per section made adding a section a change
     * to every caller.
     */
    public class Builder {
        public var cldrVersion: String = ""
        public var isoPublished: String = ""
        public var localeTags: List<String> = emptyList()
        public var countries: List<CountryInfo> = emptyList()
        public var currencies: List<CurrencyEntry> = emptyList()
        public var countryCurrencies: Map<String, String> = emptyMap()

        private val tables = LinkedHashMap<String, String>()
        private val sections = LinkedHashMap<String, Map<String, String>>()

        /** Adds a locale-independent table. The name must be declared in [BundleTables]. */
        public fun table(name: String, payload: String): Builder = apply {
            require(name in BundleTables.ALL) { "'$name' is not a declared bundle table" }
            tables[name] = payload
        }

        /** Adds a per-locale section. The name must be declared in [BundleSection.ALL]. */
        public fun section(name: String, payloads: Map<String, String>): Builder = apply {
            require(name in BundleSection.BY_NAME) { "'$name' is not a declared bundle section" }
            sections[name] = payloads
        }

        public fun build(): LocaleDataBundle {
            check(cldrVersion.isNotEmpty()) { "bundle has no CLDR version" }
            check(localeTags.isNotEmpty()) { "bundle has no locales" }
            return LocaleDataBundle(
                cldrVersion = cldrVersion,
                isoPublished = isoPublished,
                localeTags = localeTags,
                countries = countries,
                currencies = currencies,
                countryCurrencies = countryCurrencies,
                tables = tables,
                sections = sections,
            )
        }

        public companion object
    }

    public companion object {

        /**
         * The format this build writes and reads.
         *
         * Earlier versions are not read. Nothing has published to Maven Central,
         * so there is no bundle in the wild to migrate. That stops being true at
         * the first release, and then this check needs a migration path rather
         * than a rejection.
         */
        public const val FORMAT_VERSION: String = "3"

        public fun readFrom(reader: Reader): LocaleDataBundle {
            val builder = Builder()
            val countries = ArrayList<CountryInfo>()
            val currencies = ArrayList<CurrencyEntry>()
            val countryCurrencies = LinkedHashMap<String, String>()
            val localeTags = ArrayList<String>()
            val payloads = BundleSection.ALL.associate { it.name to LinkedHashMap<String, String>() }

            var section = ""
            reader.forEachLine { line ->
                if (line.startsWith("#")) {
                    section = line.removePrefix("#")
                    if (section.startsWith("bundle ")) {
                        val version = section.removePrefix("bundle ")
                        check(version == FORMAT_VERSION) {
                            "this build reads bundle format $FORMAT_VERSION but the resolved " +
                                "kotlinx-locale-codegen-data carries format $version. The Gradle plugin pins " +
                                "the emitters and not the data, so align the kotlinxLocaleCldrData dependency " +
                                "with the plugin version, or drop the explicit declaration to take the " +
                                "plugin's default."
                        }
                        section = "header"
                    }
                    return@forEachLine
                }
                if (line.isEmpty()) return@forEachLine
                val fields = line.split('\t')
                when (section) {
                    "header" -> when (fields[0]) {
                        "cldr" -> builder.cldrVersion = fields[1]
                        "iso4217" -> builder.isoPublished = fields[1]
                    }
                    "countries" -> countries += CountryInfo(fields[0], fields[1], fields[2].toInt(), fields[3])
                    "currencies" -> currencies += CurrencyEntry(
                        code = fields[0],
                        numericCode = fields[1].toInt(),
                        minorUnits = fields[2].toInt(),
                        cldrDigits = fields[3].toInt(),
                        cldrRounding = fields[4].toInt(),
                        cldrCashDigits = fields[5].toInt(),
                        cldrCashRounding = fields[6].toInt(),
                        englishName = fields[7],
                        tenderFrom = fields[8].toInt(),
                        tenderTo = fields[9].toInt(),
                        isTender = fields[10] == "1",
                        isCurrent = fields[11] == "1",
                    )
                    "countryCurrencies" -> countryCurrencies[fields[0]] = fields[1]
                    "localeTags" -> localeTags += fields[0]
                    "end" -> Unit
                    else -> {
                        // Strict, because the alternative is silent. A bundle written
                        // by a newer generator used to lose its unknown sections here,
                        // and a build narrowed against it compiled and then resolved
                        // nothing.
                        val target = payloads[section]
                        if (target != null) {
                            target[fields[0]] = fields.getOrElse(1) { "" }
                        } else if (section in BundleTables.ALL) {
                            builder.table(section, fields[0])
                        } else {
                            error(
                                "unknown bundle section '$section'. This bundle was written by a newer " +
                                    "kotlinx-locale-codegen-data than the emitters reading it.",
                            )
                        }
                    }
                }
            }

            builder.countries = countries
            builder.currencies = currencies
            builder.countryCurrencies = countryCurrencies
            builder.localeTags = localeTags
            for ((name, rows) in payloads) {
                if (rows.isNotEmpty()) builder.section(name, rows)
            }
            return builder.build()
        }
    }
}

private fun Reader.forEachLine(action: (String) -> Unit) {
    buffered().useLines { lines -> lines.forEach(action) }
}
