package dev.carcara.kotlinx.locale.codegen

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
    /** Flexible day period names ([DAY_PERIOD_TYPES] minus am/pm); "" when the locale has none. */
    val dayPeriods: List<String>,
    val dayPeriodRules: List<DayPeriodRule>,
    val era0: String,
    val era1: String,
    val dateFormats: List<String>,
    val timeFormats: List<String>,
    val glueFormats: List<String>,
    val digits: String,
)

/**
 * The skeleton half of one locale, resolved.
 *
 * Separate from [ResolvedLocaleData] because it is carried by a separate module
 * and encoded into separate bundle sections: `availableFormats` is large and
 * varies per locale, while `appendItems` is near-universal, so folding them into
 * one record would make the second dedupe as badly as the first.
 */
class ResolvedSkeletonData(
    /** Skeleton id to pattern, e.g. `yMMMd` to `d 'de' MMM 'de' y`. */
    val availableFormats: Map<String, String>,
    /** Indexed by [DATE_FIELD_TYPES]; "" where CLDR declares no append format. */
    val appendItems: List<String>,
    /** Indexed by [DATE_FIELD_TYPES]; the `{2}` an appendItem writes. "" where absent. */
    val fieldNames: List<String>,
    val quartersWide: List<String>,
    val quartersAbbr: List<String>,
    /**
     * The `atTime` date-time glue, in FULL, LONG, MEDIUM, SHORT order.
     *
     * Skeleton formatting joins a date and a time with this rather than with the
     * standard glue the style-based API uses, which is why `en` reads
     * "July 27, 2026 at 3:05 PM" here and "July 27, 2026, 3:05 PM" there.
     */
    val glueAtTimeFormats: List<String>,
    /** What the `j` skeleton letter resolves to for this locale. */
    val hourPreferred: Char,
    /** What `C` resolves to; a trailing `b` or `B` names the day period letter. */
    val hourFirstAllowed: String,
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

    private fun partial(id: String): PartialLocaleData = partialCache.getOrPut(id) { parseLdml(mainDir.resolve("$id.xml")) }

    /**
     * Day period rules for [id], resolved by plain truncation of the locale id
     * (the way ICU resolves its dayPeriods data), NOT via parentLocales.
     * zh_Hant relies on this: its parentLocales parent is root, but its
     * standard time patterns use B and expect the zh rules.
     */
    private fun dayPeriodRulesFor(id: String): List<DayPeriodRule> {
        var current = id
        while (true) {
            supplemental.dayPeriodRules[current]?.let { return it }
            current = current.substringBeforeLast('_', "")
                .ifEmpty { return supplemental.dayPeriodRules.getValue("root") }
        }
    }

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

    /**
     * Only the skeleton ids this locale's own file declares, before inheritance.
     *
     * For the ICU cross-check: a locale that overrides an id its parent also has
     * is exactly where a pruned ICU bundle answers out of the parent instead.
     */
    fun declaredAvailableFormats(id: String): Map<String, String> = partial(id).availableFormats

    /** [chain] restricted to levels that actually have a data file (plus root). */
    fun dataChain(id: String): List<String> = chain(id).filter { it == "root" || it in available }

    fun resolve(id: String): ResolvedLocaleData {
        val chain = dataChain(id)

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
        val dayPeriods = arrayOfNulls<String>(DAY_PERIOD_TYPES.size - 2)
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
            mergeList(dayPeriods, p.dayPeriods)
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
            dayPeriods = dayPeriods.map { it ?: "" },
            dayPeriodRules = dayPeriodRulesFor(id),
            era0 = checkNotNull(era0) { "$id: missing era0" },
            era1 = checkNotNull(era1) { "$id: missing era1" },
            dateFormats = full("dateFormats", dateFormats),
            timeFormats = full("timeFormats", timeFormats),
            glueFormats = full("glueFormats", glueFormats),
            digits = digits,
        )
    }

    /**
     * The skeleton tables for [id], resolved down the same chain [resolve] walks.
     *
     * `availableFormats` merges per skeleton id rather than wholesale: a locale
     * declaring only `yMMMd` still inherits the other fifty-odd ids from its
     * parent, which is why 1122 locales average 55 entries against the 49 root
     * alone declares.
     */
    fun resolveSkeletons(id: String): ResolvedSkeletonData {
        val availableFormats = LinkedHashMap<String, String>()
        val appendItems = arrayOfNulls<String>(DATE_FIELD_TYPES.size)
        val fieldNames = arrayOfNulls<String>(DATE_FIELD_TYPES.size)
        val quartersWide = arrayOfNulls<String>(4)
        val quartersAbbr = arrayOfNulls<String>(4)

        for (level in dataChain(id)) {
            val p = partial(level)
            for ((skeleton, pattern) in p.availableFormats) availableFormats.putIfAbsent(skeleton, pattern)
            for (i in appendItems.indices) {
                if (appendItems[i] == null) appendItems[i] = p.appendItems[i]
                if (fieldNames[i] == null) fieldNames[i] = p.fieldNames[i]
            }
            for (i in 0..3) {
                if (quartersWide[i] == null) quartersWide[i] = p.quartersWide[i]
                if (quartersAbbr[i] == null) quartersAbbr[i] = p.quartersAbbr[i]
            }
        }

        // root.xml aliases format abbreviated to format wide, the way it does for
        // months and days.
        for (i in 0..3) if (quartersAbbr[i] == null) quartersAbbr[i] = quartersWide[i]

        val glueAtTime = arrayOfNulls<String>(4)
        for (level in dataChain(id)) {
            val p = partial(level)
            for (i in 0..3) if (glueAtTime[i] == null) glueAtTime[i] = p.glueAtTimeFormats[i]
        }
        // root declares no atTime at all, so a locale that inherits all the way
        // up lands on its standard glue, which is what CLDR's alias says.
        val standardGlue = resolve(id).glueFormats

        val hourCycle = supplemental.hourCycleFor(id)

        // Positions stay put so the runtime can index by field, but a field no
        // skeleton can ask for carries nothing.
        fun forRenderable(values: Array<String?>): List<String> =
            values.mapIndexed { i, v -> if (DATE_FIELD_TYPES[i] in RENDERABLE_FIELDS) v.orEmpty() else "" }

        return ResolvedSkeletonData(
            availableFormats = availableFormats.toSortedMap(),
            appendItems = forRenderable(appendItems),
            fieldNames = forRenderable(fieldNames),
            quartersWide = quartersWide.mapIndexed { i, v -> checkNotNull(v) { "$id: missing quartersWide[$i]" } },
            quartersAbbr = quartersAbbr.mapIndexed { i, v -> checkNotNull(v) { "$id: missing quartersAbbr[$i]" } },
            glueAtTimeFormats = List(4) { glueAtTime[it] ?: standardGlue[it] },
            hourPreferred = hourCycle.preferred,
            hourFirstAllowed = hourCycle.firstAllowed,
        )
    }
}

/**
 * Encodes resolved data as a compact record: fields joined by U+001F,
 * list items joined by U+001E. Decoded at runtime by LocaleData.
 */
fun ResolvedLocaleData.encode(): String {
    val fields = ArrayList<String>(25)
    fun list(items: List<String>) = fields.add(items.joinToString("\u001E"))
    list(monthsWide)
    list(monthsAbbr)
    list(monthsNarrow)
    list(daysWide)
    list(daysAbbr)
    list(daysNarrow)
    fields.add(am)
    fields.add(pm)
    fields.add(era0)
    fields.add(era1)
    dateFormats.forEach(fields::add)
    timeFormats.forEach(fields::add)
    glueFormats.forEach(fields::add)
    fields.add(digits)
    list(dayPeriods)
    // Each rule as "typeCode,start,end" minutes; start == end marks a point rule.
    list(dayPeriodRules.map { "${DAY_PERIOD_TYPES.indexOf(it.type)},${it.start},${it.end}" })
    return fields.joinToString("\u001F")
}

/**
 * Field letters this library cannot render, and so will not offer a skeleton for.
 *
 * `U` is a cyclic year name, which only the non-gregorian calendars the README
 * already lists as unsupported use. `v z Z V O X x` are time zones, which a
 * `LocalDate` or `LocalTime` does not carry — the same reason
 * `withoutZoneFields()` exists and FULL and LONG times already collapse to
 * MEDIUM. `w W F` are week numbering, which needs each locale's first day of
 * week and minimum days, a supplemental data set nothing here reads yet. `g S A`
 * are a Julian day number and sub-second precision, which no standard id asks
 * for.
 *
 * Across CLDR 48.2 this drops thirteen ids, all of them zone or week:
 * `Hv Hmv Hmsv hv hmv hmsv` and their `vvvv` forms, `HHmmZ`, `MMMMW` and `yw`.
 */
private val UNSUPPORTED_FIELD_LETTERS = setOf(
    'U',
    'v', 'z', 'Z', 'V', 'O', 'X', 'x',
    'w', 'W', 'F',
    'g', 'S', 'A',
)

/**
 * The field letters of a CLDR pattern, ignoring `'quoted literals'`.
 *
 * Scanning the raw characters instead would read the `U` of German `'Uhr'`, the
 * `z` of `'zeg'` and the `g` of `'ga'` as fields and throw away forty-seven
 * perfectly renderable entries.
 */
internal fun patternFieldLetters(pattern: String): Set<Char> {
    val letters = LinkedHashSet<Char>()
    var i = 0
    while (i < pattern.length) {
        val ch = pattern[i]
        when {
            ch == '\'' -> {
                i++
                if (i < pattern.length && pattern[i] == '\'') {
                    i++ // an escaped apostrophe, not a quoted section
                } else {
                    while (i < pattern.length && pattern[i] != '\'') i++
                    i++
                }
            }
            ch in 'a'..'z' || ch in 'A'..'Z' -> {
                letters.add(ch)
                i++
            }
            else -> i++
        }
    }
    return letters
}

/**
 * Whether a skeleton id is one this library can both match and render.
 *
 * The id enumerates the fields, so it is what decides; the pattern is checked
 * only so that a future CLDR release moving an unrenderable field into a pattern
 * whose id does not mention it cannot slip through and render one field short.
 * As of CLDR 48.2 the pattern check drops nothing the id check has not.
 */
fun isSupportedSkeleton(id: String, pattern: String): Boolean = id.none { it in UNSUPPORTED_FIELD_LETTERS } &&
    patternFieldLetters(pattern).none { it in UNSUPPORTED_FIELD_LETTERS }

/** The skeleton table: `id<KEY>pattern` entries joined by the list separator. */
fun ResolvedSkeletonData.encodeFormats(): String = availableFormats.entries
    .filter { (id, pattern) -> isSupportedSkeleton(id, pattern) }
    .joinToString(LIST_SEPARATOR) { (id, pattern) -> id + KEY_SEPARATOR + pattern }

/** The appendItem patterns alone, positional against [DATE_FIELD_TYPES]. */
fun ResolvedSkeletonData.encodeAppendFormats(): String = appendItems.joinToString(LIST_SEPARATOR)

/**
 * The per-locale names and the hour cycle: field display names, wide quarters,
 * abbreviated quarters, then what `j` and `C` resolve to.
 */
fun ResolvedSkeletonData.encodeNames(): String = listOf(
    fieldNames.joinToString(LIST_SEPARATOR),
    quartersWide.joinToString(LIST_SEPARATOR),
    quartersAbbr.joinToString(LIST_SEPARATOR),
    listOf(hourPreferred.toString(), hourFirstAllowed).joinToString(LIST_SEPARATOR),
    glueAtTimeFormats.joinToString(LIST_SEPARATOR),
).joinToString(FIELD_SEPARATOR)
