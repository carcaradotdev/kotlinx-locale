package dev.carcara.kotlinx.locale.codegen

import java.io.File

/** ISO 3166-1 code mappings for one territory. */
class TerritoryCodes(
    val alpha2: String,
    val alpha3: String?,
    val numeric: Int?,
)

/** CLDR currency fraction info: how many digits CLDR formats and how it rounds. */
class CurrencyFractions(
    val digits: Int,
    val rounding: Int,
    val cashDigits: Int,
    val cashRounding: Int,
)

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
)

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
        }
    }

    return SupplementalData(
        parentOverrides = parentOverrides,
        numberingSystemDigits = digits,
        dayPeriodRules = dayPeriodRules,
        territoryCodes = territoryCodes,
        currencyFractions = currencyFractions,
        defaultFractions = defaultFractions,
        regionCurrencies = regionCurrencies,
    )
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
