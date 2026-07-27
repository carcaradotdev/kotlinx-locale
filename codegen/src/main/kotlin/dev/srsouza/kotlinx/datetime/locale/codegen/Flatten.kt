package dev.srsouza.kotlinx.datetime.locale.codegen

import java.io.File

/** Fully resolved locale data: no nulls, ready to encode. */
class ResolvedLocaleData(
    val monthsWide: List<String>,
    val monthsAbbr: List<String>,
    val monthsNarrow: List<String>,
    val daysWide: List<String>,
    val daysAbbr: List<String>,
    val daysNarrow: List<String>,
    val am: String,
    val pm: String,
    val era0: String,
    val era1: String,
    val dateFormats: List<String>,
    val timeFormats: List<String>,
    val glueFormats: List<String>,
    val digits: String,
)

class Flattener(private val cldrDir: File, private val supplemental: SupplementalData) {
    private val mainDir = cldrDir.resolve("common/main")
    private val partialCache = HashMap<String, PartialLocaleData>()

    /** All CLDR locale ids (file names without .xml), excluding root. */
    val localeIds: List<String> = mainDir.listFiles { f: File -> f.extension == "xml" }!!
        .map { it.nameWithoutExtension }
        .filter { it != "root" }
        .sorted()

    private val available = localeIds.toHashSet()

    private fun partial(id: String): PartialLocaleData =
        partialCache.getOrPut(id) { parseLdml(mainDir.resolve("$id.xml")) }

    /** Inheritance chain from the locale itself up to and including root. */
    fun chain(id: String): List<String> {
        val chain = ArrayList<String>()
        var current: String? = id
        while (current != null && current != "root") {
            chain.add(current)
            current = supplemental.parentOverrides[current]
                ?: current.substringBeforeLast('_', "").takeIf { it.isNotEmpty() }
                ?: "root"
            // Truncation can land on a locale that has no data file (e.g. an
            // intermediate script-only id); keep walking regardless, merging
            // only levels that exist.
        }
        chain.add("root")
        return chain
    }

    fun resolve(id: String): ResolvedLocaleData {
        val chain = chain(id).filter { it == "root" || it in available }

        val monthsWide = arrayOfNulls<String>(12)
        val monthsAbbr = arrayOfNulls<String>(12)
        val monthsNarrow = arrayOfNulls<String>(12)
        val monthsStandaloneNarrow = arrayOfNulls<String>(12)
        val daysWide = arrayOfNulls<String>(7)
        val daysAbbr = arrayOfNulls<String>(7)
        val daysNarrow = arrayOfNulls<String>(7)
        val daysStandaloneNarrow = arrayOfNulls<String>(7)
        var am: String? = null
        var pm: String? = null
        var era0: String? = null
        var era1: String? = null
        val dateFormats = arrayOfNulls<String>(4)
        val timeFormats = arrayOfNulls<String>(4)
        val glueFormats = arrayOfNulls<String>(4)
        var numberingSystem: String? = null

        fun mergeList(target: Array<String?>, source: Array<String?>) {
            for (i in target.indices) if (target[i] == null) target[i] = source[i]
        }

        for (level in chain) {
            val p = partial(level)
            mergeList(monthsWide, p.monthsWide)
            mergeList(monthsAbbr, p.monthsAbbr)
            mergeList(monthsNarrow, p.monthsNarrow)
            mergeList(monthsStandaloneNarrow, p.monthsStandaloneNarrow)
            mergeList(daysWide, p.daysWide)
            mergeList(daysAbbr, p.daysAbbr)
            mergeList(daysNarrow, p.daysNarrow)
            mergeList(daysStandaloneNarrow, p.daysStandaloneNarrow)
            mergeList(dateFormats, p.dateFormats)
            mergeList(timeFormats, p.timeFormats)
            mergeList(glueFormats, p.glueFormats)
            if (am == null) am = p.am
            if (pm == null) pm = p.pm
            if (era0 == null) era0 = p.era0
            if (era1 == null) era1 = p.era1
            if (numberingSystem == null) numberingSystem = p.numberingSystem
        }

        // Emulate root.xml's aliases for any slot still empty after the merge:
        // format abbreviated -> format wide, format narrow -> stand-alone narrow.
        for (i in 0..11) {
            if (monthsAbbr[i] == null) monthsAbbr[i] = monthsWide[i]
            if (monthsNarrow[i] == null) monthsNarrow[i] = monthsStandaloneNarrow[i] ?: monthsAbbr[i]
        }
        for (i in 0..6) {
            if (daysAbbr[i] == null) daysAbbr[i] = daysWide[i]
            if (daysNarrow[i] == null) daysNarrow[i] = daysStandaloneNarrow[i] ?: daysAbbr[i]
        }

        val digits = supplemental.numberingSystemDigits[numberingSystem ?: "latn"]
            ?: supplemental.numberingSystemDigits.getValue("latn")

        fun full(name: String, values: Array<String?>): List<String> =
            values.mapIndexed { i, v -> checkNotNull(v) { "$id: missing $name[$i] after flattening" } }

        return ResolvedLocaleData(
            monthsWide = full("monthsWide", monthsWide),
            monthsAbbr = full("monthsAbbr", monthsAbbr),
            monthsNarrow = full("monthsNarrow", monthsNarrow),
            daysWide = full("daysWide", daysWide),
            daysAbbr = full("daysAbbr", daysAbbr),
            daysNarrow = full("daysNarrow", daysNarrow),
            am = checkNotNull(am) { "$id: missing am" },
            pm = checkNotNull(pm) { "$id: missing pm" },
            era0 = checkNotNull(era0) { "$id: missing era0" },
            era1 = checkNotNull(era1) { "$id: missing era1" },
            dateFormats = full("dateFormats", dateFormats),
            timeFormats = full("timeFormats", timeFormats),
            glueFormats = full("glueFormats", glueFormats),
            digits = digits,
        )
    }
}

const val FIELD_SEPARATOR = "\u001F"
const val LIST_SEPARATOR = "\u001E"

/**
 * Encodes resolved data as a compact record: fields joined by U+001F,
 * list items joined by U+001E. Decoded at runtime by LocaleData.
 */
fun ResolvedLocaleData.encode(): String {
    val fields = ArrayList<String>(23)
    fun list(items: List<String>) = fields.add(items.joinToString("\u001E"))
    list(monthsWide); list(monthsAbbr); list(monthsNarrow)
    list(daysWide); list(daysAbbr); list(daysNarrow)
    fields.add(am); fields.add(pm)
    fields.add(era0); fields.add(era1)
    dateFormats.forEach(fields::add)
    timeFormats.forEach(fields::add)
    glueFormats.forEach(fields::add)
    fields.add(digits)
    return fields.joinToString("\u001F")
}
