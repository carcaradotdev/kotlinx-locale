package dev.carcara.kotlinx.locale.codegen

import org.w3c.dom.Element
import java.io.File

/** ISO 3166-1 code mappings for one territory. */
class TerritoryCodes(val alpha2: String, val alpha3: String?, val numeric: Int?)

/** CLDR currency fraction info: how many digits CLDR formats and how it rounds. */
class CurrencyFractions(val digits: Int, val rounding: Int, val cashDigits: Int, val cashRounding: Int)

/**
 * When a currency code was legal tender somewhere, and whether it ever was.
 *
 * "Somewhere" is the honest scope. CLDR records the window per region and a code
 * can be current in one and withdrawn in another, so a single answer for the
 * code can only mean "any region", which is what ICU's `Currency.isAvailable`
 * means too.
 *
 * [tender] is a different axis from withdrawal: XXX has never been withdrawn and
 * has never been tender.
 */
class CurrencyTenderWindow(val from: Int, val to: Int, val tender: Boolean)

/** A CLDR `yyyy-MM-dd`, `yyyy-MM` or `yyyy` date as a day number, or null. */
private fun epochDayOf(text: String): Int? {
    if (text.isEmpty()) return null
    val parts = text.split('-')
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
    val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
    // The proleptic Gregorian day number, computed here rather than taken from
    // kotlinx-datetime: :codegen holds no dependency on the library it writes.
    val a = (14 - month) / 12
    val y = year + 4800 - a
    val m = month + 12 * a - 3
    return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045 - 2440588
}

class SupplementalData(
    /** child locale id -> explicit parent locale id (component-less parentLocales only). */
    val parentOverrides: Map<String, String>,
    /** numbering system id -> its ten digits (numeric systems only). */
    val numberingSystemDigits: Map<String, String>,
    /** locale id -> that locale's flexible day period rules (formatting ruleset). */
    val dayPeriodRules: Map<String, List<DayPeriodRule>>,
    /** ISO 3166-1 alpha-2 -> code mappings (includes non-ISO CLDR territories). */
    val territoryCodes: Map<String, TerritoryCodes>,
    /** ISO 4217 code -> CLDR fraction info (explicit entries only, see [defaultFractions]). */
    val currencyFractions: Map<String, CurrencyFractions>,
    val defaultFractions: CurrencyFractions,
    /** region alpha-2 -> current legal-tender currency codes, preferred first. */
    val regionCurrencies: Map<String, List<String>>,
    /** ISO 4217 code -> the union of its per-region tender windows. */
    val currencyTender: Map<String, CurrencyTenderWindow>,
    /**
     * `<timeData>` keyed the way it is written: mostly regions, but with locale
     * ids mixed in (`en_IL`, `fr_CA`, `ca_ES`, `it_CH`, `it_IT` and others).
     */
    val hourCycles: Map<String, HourCycle>,
    /** language, or language_script, -> the region likely subtags maximises it to. */
    val likelyRegions: Map<String, String>,
    /** territory -> where its week starts, its minimum days, and its weekend. */
    val weekData: Map<String, WeekDataRow>,
)

/**
 * A `<weekData>` row: where the week starts, how many of its days must fall in
 * the new year for that to count as week one, and which days are the weekend.
 *
 * Days are ISO numbers, Monday 1 through Sunday 7, matching `DayOfWeek`. CLDR
 * writes the weekend as an inclusive start day and end day; the run between them
 * is walked out here so the runtime carries a set and no caller has to re-derive
 * it. The run is always contiguous but not always more than one day: Iran starts
 * and ends its weekend on Friday.
 */
class WeekDataRow(val firstDay: Int, val minDays: Int, val weekend: Set<Int>)

/** CLDR's day codes, as ISO day numbers. */
private val CLDR_DAYS = mapOf("mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7)

/**
 * A `<timeData>` row: which hour field a locale prefers, and which one the `C`
 * skeleton letter reaches for.
 *
 * [preferred] answers the `j` skeleton letter. [firstAllowed] answers `C`, which
 * takes the first *allowed* format rather than the preferred one, and whose
 * trailing `b` or `B` decides which day period letter comes with it — so `C` in
 * `hi-IN`, whose first allowed format is `hB`, asks for a flexible day period
 * where `j` would ask for AM/PM.
 */
class HourCycle(val preferred: Char, val firstAllowed: String)

/**
 * The day period types, in the order used by the encoded rule records. am and pm
 * come first so that the flexible types line up with the runtime name list
 * (whose index is `code - 2`).
 */
val DAY_PERIOD_TYPES = listOf(
    "am", "pm", "midnight", "noon",
    "morning1", "morning2", "afternoon1", "afternoon2",
    "evening1", "evening2", "night1", "night2",
)

/**
 * One rule of a day period ruleset, times as minutes of the day. A point rule
 * (`at="12:00"`, only midnight and noon) has start == end; an interval rule
 * covers [start, end) and wraps past midnight when start > end.
 */
class DayPeriodRule(val type: String, val start: Int, val end: Int) {
    val isPoint: Boolean get() = start == end
}

fun parseSupplemental(cldrDir: File): SupplementalData {
    val supplementalDir = cldrDir.resolve("common/supplemental")
    val supplementalData = parseXml(supplementalDir.resolve("supplementalData.xml")).documentElement

    val parentOverrides = LinkedHashMap<String, String>()
    for (block in supplementalData.childElements("parentLocales")) {
        if (block.hasAttribute("component")) continue
        for (rule in block.childElements("parentLocale")) {
            val parent = rule.getAttribute("parent")
            for (child in rule.getAttribute("locales").split(' ')) {
                if (child.isNotBlank()) parentOverrides[child] = parent
            }
        }
    }

    val digits = LinkedHashMap<String, String>()
    val numberingSystems = parseXml(supplementalDir.resolve("numberingSystems.xml")).documentElement
    numberingSystems.child("numberingSystems")?.let { container ->
        for (system in container.childElements("numberingSystem")) {
            if (system.getAttribute("type") != "numeric") continue
            val id = system.getAttribute("id")
            val systemDigits = system.getAttribute("digits")
            if (id.isNotEmpty() && systemDigits.isNotEmpty()) digits[id] = systemDigits
        }
    }
    check("latn" in digits) { "numberingSystems.xml did not provide latn digits" }

    val dayPeriodRules = parseDayPeriodRules(supplementalDir.resolve("dayPeriods.xml"))

    val territoryCodes = LinkedHashMap<String, TerritoryCodes>()
    supplementalData.child("codeMappings")?.let { mappings ->
        for (territory in mappings.childElements("territoryCodes")) {
            val alpha2 = territory.getAttribute("type")
            if (alpha2.isEmpty()) continue
            territoryCodes[alpha2] = TerritoryCodes(
                alpha2 = alpha2,
                alpha3 = territory.getAttribute("alpha3").takeIf(String::isNotEmpty),
                numeric = territory.getAttribute("numeric").takeIf(String::isNotEmpty)?.toIntOrNull(),
            )
        }
    }

    var defaultFractions = CurrencyFractions(digits = 2, rounding = 0, cashDigits = 2, cashRounding = 0)
    val currencyFractions = LinkedHashMap<String, CurrencyFractions>()
    val regionCurrencies = LinkedHashMap<String, List<String>>()
    val currencyTender = LinkedHashMap<String, CurrencyTenderWindow>()
    supplementalData.child("currencyData")?.let { currencyData ->
        currencyData.child("fractions")?.let { fractions ->
            for (info in fractions.childElements("info")) {
                val code = info.getAttribute("iso4217")
                val infoDigits = info.getAttribute("digits").toIntOrNull() ?: continue
                val rounding = info.getAttribute("rounding").toIntOrNull() ?: 0
                val parsed = CurrencyFractions(
                    digits = infoDigits,
                    rounding = rounding,
                    cashDigits = info.getAttribute("cashDigits").toIntOrNull() ?: infoDigits,
                    cashRounding = info.getAttribute("cashRounding").toIntOrNull() ?: rounding,
                )
                if (code == "DEFAULT") defaultFractions = parsed else currencyFractions[code] = parsed
            }
        }
        for (region in currencyData.childElements("region")) {
            val alpha2 = region.getAttribute("iso3166")
            if (alpha2.isEmpty()) continue
            val current = region.childElements("currency")
                .filter { !it.hasAttribute("to") && it.getAttribute("tender") != "false" }
                .map { it.getAttribute("iso4217") }
                .filter(String::isNotEmpty)
            if (current.isNotEmpty()) regionCurrencies[alpha2] = current

            // A different question from the one above, aggregated across every
            // region rather than filtered to the current ones: when was this
            // code legal tender anywhere, and is it a tender code at all.
            for (currency in region.childElements("currency")) {
                val code = currency.getAttribute("iso4217").takeIf { it.isNotEmpty() } ?: continue
                val from = epochDayOf(currency.getAttribute("from")) ?: Int.MIN_VALUE
                val to = epochDayOf(currency.getAttribute("to")) ?: Int.MAX_VALUE
                val tender = currency.getAttribute("tender") != "false"
                val existing = currencyTender[code]
                currencyTender[code] = CurrencyTenderWindow(
                    from = minOf(existing?.from ?: from, from),
                    to = maxOf(existing?.to ?: to, to),
                    tender = (existing?.tender ?: false) || tender,
                )
            }
        }
    }

    // ICU builds one list per key as [preferred] + allowed, then reads the
    // preferred hour char off the head and the C letter off allowed[0].
    val hourCycles = LinkedHashMap<String, HourCycle>()
    supplementalData.child("timeData")?.let { timeData ->
        for (hours in timeData.childElements("hours")) {
            val allowed = hours.getAttribute("allowed").split(' ').filter(String::isNotBlank)
            val preferred = hours.getAttribute("preferred").takeIf(String::isNotEmpty)
                ?: allowed.firstOrNull()
                ?: "H"
            val cycle = HourCycle(preferred = preferred[0], firstAllowed = allowed.firstOrNull() ?: preferred)
            for (key in hours.getAttribute("regions").split(' ')) {
                if (key.isNotBlank()) hourCycles[key] = cycle
            }
        }
    }
    check("001" in hourCycles) { "supplementalData.xml timeData has no 001 row" }

    val likelyRegions = LinkedHashMap<String, String>()
    val likelySubtags = parseXml(supplementalDir.resolve("likelySubtags.xml")).documentElement
    likelySubtags.child("likelySubtags")?.let { container ->
        for (entry in container.childElements("likelySubtag")) {
            val from = entry.getAttribute("from")
            // Only language and language_script keys matter: a locale that already
            // names a region does not need maximising.
            if (from.isEmpty() || from.count { it == '_' } > 1) continue
            val region = entry.getAttribute("to").split('_').lastOrNull() ?: continue
            if (region.length == 2 || (region.length == 3 && region.all(Char::isDigit))) likelyRegions[from] = region
        }
    }
    check("und" in likelyRegions) { "likelySubtags.xml has no und entry" }

    return SupplementalData(
        parentOverrides = parentOverrides,
        numberingSystemDigits = digits,
        dayPeriodRules = dayPeriodRules,
        territoryCodes = territoryCodes,
        currencyFractions = currencyFractions,
        currencyTender = currencyTender,
        defaultFractions = defaultFractions,
        regionCurrencies = regionCurrencies,
        hourCycles = hourCycles,
        likelyRegions = likelyRegions,
        weekData = parseWeekData(supplementalData),
    )
}

/**
 * The hour cycle for a CLDR locale id, resolved the way ICU resolves it.
 *
 * Region first, from the id or from likely subtags when the id carries none;
 * then `language_region` before `region` alone, because `<timeData>` is not
 * purely region keyed — `it_CH` and `it_IT` sit in a different row from `CH` and
 * `IT`. Resolved here rather than at runtime because the maximisation step needs
 * likely subtags, which is a table nothing else in the shipped artifacts reads.
 */
fun SupplementalData.hourCycleFor(cldrId: String): HourCycle {
    val parts = cldrId.split('_')
    val language = parts[0]
    val script = parts.getOrNull(1)?.takeIf { it.length == 4 }
    val region = parts.drop(1).firstOrNull { it.length == 2 || (it.length == 3 && it.all(Char::isDigit)) }
        ?: likelyRegions[if (script != null) "${language}_$script" else language]
        ?: likelyRegions[language]
        ?: likelyRegions.getValue("und")

    return hourCycles["${language}_$region"]
        ?: hourCycles[region]
        ?: hourCycles.getValue("001")
}

/**
 * `<weekData>`, resolved to one row per territory CLDR names.
 *
 * Every element fans a single value out over a space-separated territory list,
 * so the four tables are read the same way and then joined. A territory that
 * declares only some of the four inherits the rest from `001`, which is why the
 * rows are assembled here rather than left as four maps.
 */
private fun parseWeekData(supplementalData: Element): Map<String, WeekDataRow> {
    val weekData = supplementalData.child("weekData") ?: error("supplementalData.xml has no weekData block")

    // `alt` marks a value CLDR records without meaning it. release-48-2 carries
    // exactly one, a Sunday first day for GB sourced from a dictionary, and it is
    // written after the row that puts GB in the Monday list, so taking it would
    // silently overwrite the real answer. Nothing else in this file filters
    // `alt`, so the skip is spelled out rather than assumed.
    fun rows(tag: String, attribute: String): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        for (row in weekData.childElements(tag)) {
            if (row.hasAttribute("alt")) continue
            val value = row.getAttribute(attribute).takeIf(String::isNotEmpty) ?: continue
            for (territory in row.getAttribute("territories").split(' ')) {
                if (territory.isNotBlank()) values[territory] = value
            }
        }
        return values
    }

    val firstDay = rows("firstDay", "day")
    val minDays = rows("minDays", "count")
    val weekendStart = rows("weekendStart", "day")
    val weekendEnd = rows("weekendEnd", "day")

    for ((name, table) in listOf(
        "firstDay" to firstDay,
        "minDays" to minDays,
        "weekendStart" to weekendStart,
        "weekendEnd" to weekendEnd,
    )) {
        check("001" in table) { "weekData: $name has no 001 row to fall back to" }
    }
    // The canary for the skip above: GB is declared Monday and only the variant
    // row says otherwise, so this fails the build if that filter ever comes out.
    check(firstDay["GB"] == "mon") { "weekData: the alt=\"variant\" row overwrote GB's first day" }

    fun dayOf(table: Map<String, String>, territory: String): Int {
        val code = table[territory] ?: table.getValue("001")
        return CLDR_DAYS[code] ?: error("weekData: unknown day code '$code'")
    }

    val territories = (firstDay.keys + minDays.keys + weekendStart.keys + weekendEnd.keys).toSortedSet()
    return territories.associateWithTo(LinkedHashMap()) { territory ->
        val start = dayOf(weekendStart, territory)
        val end = dayOf(weekendEnd, territory)
        val weekend = LinkedHashSet<Int>()
        var day = start
        while (true) {
            weekend.add(day)
            if (day == end) break
            day = day % 7 + 1
        }
        WeekDataRow(
            firstDay = dayOf(firstDay, territory),
            minDays = (minDays[territory] ?: minDays.getValue("001")).toInt(),
            weekend = weekend,
        )
    }
}

/**
 * The week data as two flat fields: every territory CLDR names, then the
 * languages whose likely region answers something other than the world default.
 *
 * The overlay is there because a `Locale` need not carry a region, and resolving
 * `en` to the United States takes likely subtags, a table none of the shipped
 * artifacts read. Restricting it to languages that differ from `001` keeps it to
 * the rows that change an answer.
 *
 * Each value packs into four characters: the first day, the minimum days, and a
 * two-digit hex weekend mask over the same ISO day numbers.
 */
fun SupplementalData.encodeWeekData(): String {
    fun pack(row: WeekDataRow): String {
        val mask = row.weekend.fold(0) { acc, day -> acc or (1 shl (day - 1)) }
        return "${row.firstDay}${row.minDays}" + mask.toString(16).padStart(2, '0')
    }

    val world = pack(weekData.getValue("001"))
    val territories = weekData.entries.joinToString(LIST_SEPARATOR) { (territory, row) ->
        territory + KEY_SEPARATOR + pack(row)
    }

    fun packedFor(key: String): String? = likelyRegions[key]?.let { weekData[it] }?.let(::pack)

    // A row is worth carrying when it differs from what the lookup would
    // otherwise land on, which is not the same as differing from the world.
    // The runtime tries `language_script`, then `language`, then `001`, so a
    // script-qualified row has to be compared against its own language rather
    // than against the default. Cantonese is the case that proves it: `yue_Hans`
    // maximises to China and agrees with the world, while bare `yue` maximises to
    // Hong Kong and does not, so dropping the first for agreeing with `001`
    // leaves it inheriting a Sunday it never asked for.
    val overlay = LinkedHashMap<String, String>()
    for (key in likelyRegions.keys.filter { '_' !in it }.sorted()) {
        val packed = packedFor(key) ?: continue
        if (packed != world) overlay[key] = packed
    }
    for (key in likelyRegions.keys.filter { '_' in it }.sorted()) {
        val packed = packedFor(key) ?: continue
        if (packed != (overlay[key.substringBefore('_')] ?: world)) overlay[key] = packed
    }

    val encoded = overlay.entries
        .sortedBy { it.key }
        .joinToString(LIST_SEPARATOR) { (key, packed) -> key + KEY_SEPARATOR + packed }

    return territories + FIELD_SEPARATOR + encoded
}

private fun parseDayPeriodRules(file: File): Map<String, List<DayPeriodRule>> {
    val root = parseXml(file).documentElement
    // The first (untyped) ruleset drives formatting with the B pattern field;
    // the type="selection" ruleset is for message selection and is not used here.
    val ruleSet = root.childElements("dayPeriodRuleSet").firstOrNull { !it.hasAttribute("type") }
        ?: error("dayPeriods.xml has no formatting dayPeriodRuleSet")

    fun minutes(value: String): Int {
        val (h, m) = value.split(':').map(String::toInt)
        return h * 60 + m
    }

    val rulesByLocale = LinkedHashMap<String, List<DayPeriodRule>>()
    for (rulesEl in ruleSet.childElements("dayPeriodRules")) {
        val rules = ArrayList<DayPeriodRule>()
        for (rule in rulesEl.childElements("dayPeriodRule")) {
            val type = rule.getAttribute("type")
            check(type in DAY_PERIOD_TYPES) { "dayPeriods.xml: unknown day period type '$type'" }
            if (rule.hasAttribute("at")) {
                // A point rule; 24:00 is the same instant as 00:00.
                val at = minutes(rule.getAttribute("at")) % (24 * 60)
                rules.add(DayPeriodRule(type, at, at))
            } else {
                val start = minutes(rule.getAttribute("from")) % (24 * 60)
                val end = minutes(rule.getAttribute("before"))
                check(start != end) { "dayPeriods.xml: zero-length interval for '$type'" }
                rules.add(DayPeriodRule(type, start, end))
            }
        }
        // Points first: the runtime scans in order and midnight/noon must win
        // over the interval that contains the same minute.
        rules.sortBy { !it.isPoint }
        for (locale in rulesEl.getAttribute("locales").split(' ')) {
            if (locale.isNotBlank()) rulesByLocale[locale] = rules
        }
    }
    check("root" in rulesByLocale) { "dayPeriods.xml did not provide root rules" }
    return rulesByLocale
}
