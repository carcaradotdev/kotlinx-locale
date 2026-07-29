package dev.carcara.kotlinx.locale.codegen

import java.io.BufferedWriter
import java.io.Reader

/** One ISO 3166-1 country, with the English name that becomes its KDoc. */
public class CountryInfo(public val alpha2: String, public val alpha3: String, public val numeric: Int, public val englishName: String)

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
)

/**
 * Everything the emitters need, already resolved out of CLDR.
 *
 * This is the intermediate the pipeline always had: [Flattener] resolves
 * inheritance and each locale encodes to one compact string. Publishing it is
 * what lets a user's build generate a narrowed source set without cloning CLDR
 * — there is no network beyond dependency resolution, and the CLDR version is
 * visible in their lock file.
 *
 * The name payloads are sparse and carry their parent tag, so narrowing has to
 * keep a locale's ancestors; see [narrowTo].
 */
public class LocaleDataBundle(
    public val cldrVersion: String,
    public val isoPublished: String,
    /** Canonical BCP 47 tags, without root. */
    public val localeTags: List<String>,
    public val countries: List<CountryInfo>,
    public val currencies: List<CurrencyEntry>,
    /** alpha-2 -> space-separated currency codes, preferred first. */
    public val countryCurrencies: Map<String, String>,
    /** Fully resolved datetime records, including `root`. */
    public val dateTime: Map<String, String>,
    /** Sparse country-name records carrying their parent tag, including `root`. */
    public val countryNames: Map<String, String>,
    /** Fully resolved number-format records, including `root`. */
    public val currencyFormats: Map<String, String>,
    /** Sparse currency symbol and name records carrying their parent tag, including `root`. */
    public val currencyNames: Map<String, String>,
) {

    /**
     * The same bundle carrying only [tags], the locales they inherit from, and
     * root.
     *
     * Ancestors are not optional. A country-name record holds only what that
     * locale's own CLDR file declares and points at its parent for the rest, so
     * a build that kept `pt-BR` and dropped `pt` would resolve almost nothing.
     */
    public fun narrowTo(tags: Set<String>): LocaleDataBundle {
        val kept = LinkedHashSet<String>()
        for (tag in tags) {
            require(tag in dateTime) { "no CLDR data for locale '$tag'" }
            kept += tag
            kept += ancestorsOf(tag, countryNames)
            kept += ancestorsOf(tag, currencyNames)
        }
        kept += "root"
        return LocaleDataBundle(
            cldrVersion = cldrVersion,
            isoPublished = isoPublished,
            localeTags = localeTags.filter { it in kept },
            countries = countries,
            currencies = currencies,
            countryCurrencies = countryCurrencies,
            dateTime = dateTime.filterKeys { it in kept },
            countryNames = countryNames.filterKeys { it in kept },
            currencyFormats = currencyFormats.filterKeys { it in kept },
            currencyNames = currencyNames.filterKeys { it in kept },
        )
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

        section("bundle 1")
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
            )
        }

        section("countryCurrencies")
        for ((alpha2, codes) in countryCurrencies) row(alpha2, codes)

        section("localeTags")
        for (tag in localeTags) row(tag)

        for ((name, payloads) in listOf(
            "dateTime" to dateTime,
            "countryNames" to countryNames,
            "currencyFormats" to currencyFormats,
            "currencyNames" to currencyNames,
        )) {
            section(name)
            for ((tag, payload) in payloads) row(tag, payload)
        }

        section("end")
        out.flush()
    }

    public companion object {

        public fun readFrom(reader: Reader): LocaleDataBundle {
            var cldrVersion = ""
            var isoPublished = ""
            val localeTags = ArrayList<String>()
            val countries = ArrayList<CountryInfo>()
            val currencies = ArrayList<CurrencyEntry>()
            val countryCurrencies = LinkedHashMap<String, String>()
            val payloads = mapOf(
                "dateTime" to LinkedHashMap<String, String>(),
                "countryNames" to LinkedHashMap(),
                "currencyFormats" to LinkedHashMap(),
                "currencyNames" to LinkedHashMap(),
            )

            var section = ""
            reader.forEachLine { line ->
                if (line.startsWith("#")) {
                    section = line.removePrefix("#")
                    if (section.startsWith("bundle ")) {
                        val version = section.removePrefix("bundle ")
                        check(version == "1") { "unsupported bundle format version '$version'" }
                        section = "header"
                    }
                    return@forEachLine
                }
                if (line.isEmpty()) return@forEachLine
                val fields = line.split('\t')
                when (section) {
                    "header" -> when (fields[0]) {
                        "cldr" -> cldrVersion = fields[1]
                        "iso4217" -> isoPublished = fields[1]
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
                    )
                    "countryCurrencies" -> countryCurrencies[fields[0]] = fields[1]
                    "localeTags" -> localeTags += fields[0]
                    else -> payloads[section]?.put(fields[0], fields.getOrElse(1) { "" })
                }
            }

            check(cldrVersion.isNotEmpty()) { "bundle has no CLDR version" }
            check(localeTags.isNotEmpty()) { "bundle has no locales" }
            return LocaleDataBundle(
                cldrVersion = cldrVersion,
                isoPublished = isoPublished,
                localeTags = localeTags,
                countries = countries,
                currencies = currencies,
                countryCurrencies = countryCurrencies,
                dateTime = payloads.getValue("dateTime"),
                countryNames = payloads.getValue("countryNames"),
                currencyFormats = payloads.getValue("currencyFormats"),
                currencyNames = payloads.getValue("currencyNames"),
            )
        }
    }
}

private fun Reader.forEachLine(action: (String) -> Unit) {
    buffered().useLines { lines -> lines.forEach(action) }
}
