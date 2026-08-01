package dev.carcara.kotlinx.locale.codegen

import java.io.File

/**
 * The country/currency subset of one LDML file: localized display names and the
 * number-formatting data the currency formatter needs. Like [PartialLocaleData],
 * everything is sparse — a file only carries what differs from its parent.
 */
class PartialLocaleExtras {
    /** alpha-2 -> localized country name, only entries this file declares. */
    val territoryNames = LinkedHashMap<String, String>()

    /** currency code -> localized symbol. */
    val currencySymbols = LinkedHashMap<String, String>()

    /** currency code -> localized display name (count-less form). */
    val currencyNames = LinkedHashMap<String, String>()

    /** language subtag or CLDR locale id -> its name, only what this file declares. */
    val languageNames = LinkedHashMap<String, String>()

    /** The same, keyed with a `#short` suffix, for CLDR's `alt="short"` spellings. */
    val languageShortNames = LinkedHashMap<String, String>()

    /** ISO 15924 code -> localized script name. */
    val scriptNames = LinkedHashMap<String, String>()

    /**
     * Every territory this file names, macro-regions included.
     *
     * Wider than [territoryNames], which is filtered to the ISO 3166-1 countries
     * the enum carries. A locale identifier can hold `419` or `EU`, and naming
     * `es-419` needs one.
     */
    val allTerritoryNames = LinkedHashMap<String, String>()

    /**
     * `contextTransforms` as a bit per usage and context.
     *
     * Null where the file declares no transforms at all, which is what lets a
     * locale inherit its parent's rather than silently declaring none.
     */
    var capitalization: Int? = null

    var localePattern: String? = null
    var localeSeparator: String? = null
    var localeKeyTypePattern: String? = null

    var defaultNumberingSystem: String? = null
    var minimumGroupingDigits: Int? = null

    /** numbering system attribute value ("" when absent) -> declared symbols. */
    val symbols = LinkedHashMap<String, PartialNumberSymbols>()

    /** numbering system attribute value ("" when absent) -> declared currency patterns. */
    val currencyPatterns = LinkedHashMap<String, PartialCurrencyPatterns>()

    /** numbering system -> the plain decimal pattern. */
    val decimalPatterns = LinkedHashMap<String, String>()

    /** numbering system -> the percent pattern. */
    val percentPatterns = LinkedHashMap<String, String>()

    /** numbering system -> "magnitude:count[:a]" -> compact decimal pattern, short length. */
    val compactShort = LinkedHashMap<String, MutableMap<String, String>>()

    /** The same for the long length. */
    val compactLong = LinkedHashMap<String, MutableMap<String, String>>()

    /** The same for compact currency, which CLDR only gives a short length. */
    val currencyCompact = LinkedHashMap<String, MutableMap<String, String>>()
}

class PartialNumberSymbols {
    var decimal: String? = null
    var group: String? = null
    var currencyDecimal: String? = null
    var currencyGroup: String? = null
    var minusSign: String? = null
    var plusSign: String? = null
    var percentSign: String? = null
    var perMille: String? = null
    var approximatelySign: String? = null
    var exponential: String? = null
    var superscriptingExponent: String? = null
    var infinity: String? = null
    var nan: String? = null
    var list: String? = null
    var timeSeparator: String? = null
}

class PartialCurrencyPatterns {
    var standard: String? = null
    var standardAlpha: String? = null
    var accounting: String? = null
    var accountingAlpha: String? = null
}

/**
 * The `contextTransforms` usages this library carries, two bits each.
 *
 * The rest of CLDR's usages name things this library does not format:
 * `keyValue`, `typographicNames`, `number-spellout`, the era fields.
 */
val CAPITALIZATION_USAGES: List<String> = listOf(
    "month-format-except-narrow",
    "month-standalone-except-narrow",
    "day-format-except-narrow",
    "day-standalone-except-narrow",
    "languages",
    "script",
    "territory",
    "relative",
    "currencyName",
)

/** Fully resolved number-formatting data for the currency formatter. */
class ResolvedCurrencyFormat(
    val digits: String,
    val decimal: String,
    val group: String,
    val currencyDecimal: String,
    val currencyGroup: String,
    val minusSign: String,
    val minimumGroupingDigits: Int,
    val standardPattern: String,
    val standardAlphaPattern: String,
    val accountingPattern: String,
    val accountingAlphaPattern: String,
)

fun parseLocaleExtras(file: File, countryCodes: Set<String>, currencyCodes: Set<String>): PartialLocaleExtras {
    val extras = PartialLocaleExtras()
    val ldml = parseXml(file).documentElement

    ldml.child("localeDisplayNames")?.let { displayNames ->
        displayNames.child("territories")?.let { territories ->
            for (territory in territories.childElements("territory")) {
                if (territory.hasAttribute("alt")) continue
                val type = territory.getAttribute("type")
                val name = territory.textContent.cleaned() ?: continue
                extras.allTerritoryNames.putIfAbsent(type, name)
                if (type in countryCodes) extras.territoryNames.putIfAbsent(type, name)
            }
        }

        displayNames.child("languages")?.let { languages ->
            for (language in languages.childElements("language")) {
                val type = language.getAttribute("type")
                if (type.isEmpty()) continue
                // menu= reorders a name for an alphabetical list, which is a
                // different question from what the name is.
                if (language.hasAttribute("menu")) continue
                val name = language.textContent.cleaned() ?: continue
                when (language.getAttribute("alt")) {
                    "" -> extras.languageNames.putIfAbsent(type, name)
                    "short" -> extras.languageShortNames.putIfAbsent(type, name)
                }
            }
        }

        displayNames.child("scripts")?.let { scripts ->
            for (script in scripts.childElements("script")) {
                if (script.hasAttribute("alt")) continue
                val type = script.getAttribute("type")
                val name = script.textContent.cleaned() ?: continue
                extras.scriptNames.putIfAbsent(type, name)
            }
        }

        displayNames.child("localeDisplayPattern")?.let { patterns ->
            if (extras.localePattern == null) {
                extras.localePattern = patterns.child("localePattern")?.textContent?.cleaned()
            }
            if (extras.localeSeparator == null) {
                extras.localeSeparator = patterns.child("localeSeparator")?.textContent?.cleaned()
            }
            if (extras.localeKeyTypePattern == null) {
                extras.localeKeyTypePattern = patterns.child("localeKeyTypePattern")?.textContent?.cleaned()
            }
        }
    }

    ldml.child("contextTransforms")?.let { transforms ->
        var bits = 0
        for (usage in transforms.childElements("contextTransformUsage")) {
            val index = CAPITALIZATION_USAGES.indexOf(usage.getAttribute("type"))
            if (index < 0) continue
            for (transform in usage.childElements("contextTransform")) {
                if (transform.textContent.cleaned() != "titlecase-firstword") continue
                when (transform.getAttribute("type")) {
                    "stand-alone" -> bits = bits or (1 shl (index * 2))
                    "uiListOrMenu" -> bits = bits or (1 shl (index * 2 + 1))
                }
            }
        }
        // Recorded even when zero, because a locale that declares the element
        // and no title-casing means it, and inheriting its parent's would be
        // wrong. 252 locales write lower-case month names and declare nothing,
        // which is why "just title-case it" is not an option.
        extras.capitalization = bits
    }

    val numbers = ldml.child("numbers") ?: return extras

    numbers.child("currencies")?.let { currencies ->
        for (currency in currencies.childElements("currency")) {
            val code = currency.getAttribute("type")
            if (code !in currencyCodes) continue
            currency.childElements("symbol")
                .firstOrNull { !it.hasAttribute("alt") }
                ?.textContent?.cleaned()
                ?.let { extras.currencySymbols.putIfAbsent(code, it) }
            currency.childElements("displayName")
                .firstOrNull { !it.hasAttribute("alt") && !it.hasAttribute("count") }
                ?.textContent?.cleaned()
                ?.let { extras.currencyNames.putIfAbsent(code, it) }
        }
    }

    numbers.childElements("defaultNumberingSystem")
        .firstOrNull { !it.hasAttribute("alt") }
        ?.textContent?.cleaned()
        ?.let { extras.defaultNumberingSystem = it }

    numbers.childElements("minimumGroupingDigits")
        .firstOrNull { !it.hasAttribute("alt") }
        ?.textContent?.cleaned()?.toIntOrNull()
        ?.let { extras.minimumGroupingDigits = it }

    for (symbolsEl in numbers.childElements("symbols")) {
        if (symbolsEl.hasAttribute("alt")) continue
        val target = extras.symbols.getOrPut(symbolsEl.getAttribute("numberSystem")) { PartialNumberSymbols() }
        fun read(tag: String): String? = symbolsEl.childElements(tag)
            .firstOrNull { !it.hasAttribute("alt") }
            ?.textContent?.cleaned()
        if (target.decimal == null) target.decimal = read("decimal")
        if (target.group == null) target.group = read("group")
        if (target.currencyDecimal == null) target.currencyDecimal = read("currencyDecimal")
        if (target.currencyGroup == null) target.currencyGroup = read("currencyGroup")
        if (target.minusSign == null) target.minusSign = read("minusSign")
        if (target.plusSign == null) target.plusSign = read("plusSign")
        if (target.percentSign == null) target.percentSign = read("percentSign")
        if (target.perMille == null) target.perMille = read("perMille")
        if (target.approximatelySign == null) target.approximatelySign = read("approximatelySign")
        if (target.exponential == null) target.exponential = read("exponential")
        if (target.superscriptingExponent == null) target.superscriptingExponent = read("superscriptingExponent")
        if (target.infinity == null) target.infinity = read("infinity")
        if (target.nan == null) target.nan = read("nan")
        if (target.list == null) target.list = read("list")
        if (target.timeSeparator == null) target.timeSeparator = read("timeSeparator")
    }

    // The plain decimal and percent patterns, and the compact tables beside them.
    // A block that only carries an <alias> resolves to nothing here and falls
    // through to the next numbering system key, which is what root does for the
    // systems it does not spell out.
    for (formatsEl in numbers.childElements("decimalFormats")) {
        val system = formatsEl.getAttribute("numberSystem")
        for (length in formatsEl.childElements("decimalFormatLength")) {
            val format = length.childElements("decimalFormat").firstOrNull() ?: continue
            when (length.getAttribute("type")) {
                "" -> format.childElements("pattern")
                    .firstOrNull { !it.hasAttribute("alt") && !it.hasAttribute("type") }
                    ?.textContent?.cleaned()
                    ?.let { extras.decimalPatterns.putIfAbsent(system, it) }
                "short" -> readCompact(format, extras.compactShort.getOrPut(system) { LinkedHashMap() })
                "long" -> readCompact(format, extras.compactLong.getOrPut(system) { LinkedHashMap() })
            }
        }
    }

    for (formatsEl in numbers.childElements("percentFormats")) {
        val system = formatsEl.getAttribute("numberSystem")
        val length = formatsEl.childElements("percentFormatLength")
            .firstOrNull { !it.hasAttribute("type") } ?: continue
        length.childElements("percentFormat").firstOrNull()
            ?.childElements("pattern")?.firstOrNull { !it.hasAttribute("alt") }
            ?.textContent?.cleaned()
            ?.let { extras.percentPatterns.putIfAbsent(system, it) }
    }

    for (formatsEl in numbers.childElements("currencyFormats")) {
        val system = formatsEl.getAttribute("numberSystem")
        formatsEl.childElements("currencyFormatLength")
            .firstOrNull { it.getAttribute("type") == "short" }
            ?.childElements("currencyFormat")
            ?.firstOrNull { it.getAttribute("type").let { type -> type == "standard" || type.isEmpty() } }
            ?.let { readCompact(it, extras.currencyCompact.getOrPut(system) { LinkedHashMap() }) }

        val target = extras.currencyPatterns.getOrPut(system) { PartialCurrencyPatterns() }
        val length = formatsEl.childElements("currencyFormatLength")
            .firstOrNull { !it.hasAttribute("type") } ?: continue
        for (format in length.childElements("currencyFormat")) {
            fun pattern(alt: String?): String? = format.childElements("pattern")
                .firstOrNull { it.getAttribute("alt") == (alt ?: "") }
                ?.textContent?.cleaned()
            when (format.getAttribute("type")) {
                "standard", "" -> {
                    if (target.standard == null) target.standard = pattern(null)
                    if (target.standardAlpha == null) target.standardAlpha = pattern("alphaNextToNumber")
                }
                "accounting" -> {
                    if (target.accounting == null) target.accounting = pattern(null)
                    if (target.accountingAlpha == null) target.accountingAlpha = pattern("alphaNextToNumber")
                }
            }
        }
    }

    return extras
}

/**
 * Reads one compact block into `magnitude:count[:a]` keys.
 *
 * The `type` attribute is the bucket as a power of ten written out (`10000`),
 * stored here as the exponent. A pattern whose text is exactly `0` is kept
 * rather than skipped: UTS #35 uses it to mean "no compact form at this
 * magnitude, use the standard pattern", and ten locales set it deliberately to
 * override a parent that had one.
 */
private fun readCompact(format: org.w3c.dom.Element, target: MutableMap<String, String>) {
    for (pattern in format.childElements("pattern")) {
        val type = pattern.getAttribute("type").takeIf { it.isNotEmpty() } ?: continue
        val count = pattern.getAttribute("count").takeIf { it.isNotEmpty() } ?: "other"
        val text = pattern.textContent.cleaned() ?: continue
        val magnitude = type.length - 1
        val alt = if (pattern.getAttribute("alt") == "alphaNextToNumber") ":a" else ""
        target.putIfAbsent("$magnitude:$count$alt", text)
    }
}

/** Fully resolved number symbols for one locale. */
class ResolvedNumberSymbols(
    val numberingSystem: String,
    val digits: String,
    val decimal: String,
    val group: String,
    val currencyDecimal: String,
    val currencyGroup: String,
    val minusSign: String,
    val plusSign: String,
    val percentSign: String,
    val perMille: String,
    val approximatelySign: String,
    val exponential: String,
    val superscriptingExponent: String,
    val infinity: String,
    val nan: String,
    val listSeparator: String,
    val timeSeparator: String,
    val minimumGroupingDigits: Int,
)

/** The plain decimal and percent patterns for one locale. */
class ResolvedNumberPatterns(val decimal: String, val percent: String)

/**
 * Parses and caches [PartialLocaleExtras] per locale, resolving values across the
 * same inheritance chain the [Flattener] uses.
 */
class ExtrasResolver(
    cldrDir: File,
    private val flattener: Flattener,
    private val supplemental: SupplementalData,
    private val countryCodes: Set<String>,
    private val currencyCodes: Set<String>,
) {
    private val mainDir = cldrDir.resolve("common/main")
    private val cache = HashMap<String, PartialLocaleExtras>()

    fun partial(id: String): PartialLocaleExtras = cache.getOrPut(id) {
        parseLocaleExtras(mainDir.resolve("$id.xml"), countryCodes, currencyCodes)
    }

    /** Resolves one value across the chain, e.g. the English name of a territory. */
    fun <T : Any> resolveValue(id: String, selector: (PartialLocaleExtras) -> T?): T? =
        flattener.dataChain(id).firstNotNullOfOrNull { selector(partial(it)) }

    /**
     * Resolves number-formatting data for [id]. Symbols and patterns are looked up
     * for the locale's default numbering system across the whole chain first, then
     * for latn (CLDR's final fallback; root only declares latn).
     */
    fun resolveCurrencyFormat(id: String): ResolvedCurrencyFormat {
        val partials = flattener.dataChain(id).map(::partial)
        val numberingSystem = partials.firstNotNullOfOrNull { it.defaultNumberingSystem } ?: "latn"
        val systemKeys = listOf(numberingSystem, "latn", "")

        fun symbol(selector: (PartialNumberSymbols) -> String?): String? {
            for (key in systemKeys) {
                for (partial in partials) {
                    partial.symbols[key]?.let(selector)?.let { return it }
                }
            }
            return null
        }

        fun pattern(selector: (PartialCurrencyPatterns) -> String?): String? {
            for (key in systemKeys) {
                for (partial in partials) {
                    partial.currencyPatterns[key]?.let(selector)?.let { return it }
                }
            }
            return null
        }

        val decimal = symbol { it.decimal } ?: "."
        val group = symbol { it.group } ?: ","
        val standard = pattern { it.standard }
            ?: error("$id: no standard currency pattern after resolution")
        val accounting = pattern { it.accounting } ?: standard
        return ResolvedCurrencyFormat(
            digits = supplemental.numberingSystemDigits[numberingSystem]
                ?: supplemental.numberingSystemDigits.getValue("latn"),
            decimal = decimal,
            group = group,
            currencyDecimal = symbol { it.currencyDecimal } ?: decimal,
            currencyGroup = symbol { it.currencyGroup } ?: group,
            minusSign = symbol { it.minusSign } ?: "-",
            minimumGroupingDigits = partials.firstNotNullOfOrNull { it.minimumGroupingDigits } ?: 1,
            standardPattern = standard,
            standardAlphaPattern = pattern { it.standardAlpha } ?: standard,
            accountingPattern = accounting,
            accountingAlphaPattern = pattern { it.accountingAlpha } ?: accounting,
        )
    }

    /**
     * The whole symbol table for [id].
     *
     * The lookup is numbering-system-major and chain-minor, the same ordering
     * [resolveCurrencyFormat] uses and the one CLDR prescribes: a locale's own
     * system wins over its parent's, and `latn` is the final fallback because
     * root declares only that.
     */
    fun resolveNumberSymbols(id: String): ResolvedNumberSymbols {
        val partials = flattener.dataChain(id).map(::partial)
        val numberingSystem = partials.firstNotNullOfOrNull { it.defaultNumberingSystem } ?: "latn"
        val systemKeys = listOf(numberingSystem, "latn", "")

        fun symbol(selector: (PartialNumberSymbols) -> String?): String? {
            for (key in systemKeys) {
                for (partial in partials) {
                    partial.symbols[key]?.let(selector)?.let { return it }
                }
            }
            return null
        }

        val decimal = symbol { it.decimal } ?: "."
        val group = symbol { it.group } ?: ","
        return ResolvedNumberSymbols(
            numberingSystem = numberingSystem,
            digits = supplemental.numberingSystemDigits[numberingSystem]
                ?: supplemental.numberingSystemDigits.getValue("latn"),
            decimal = decimal,
            group = group,
            currencyDecimal = symbol { it.currencyDecimal } ?: decimal,
            currencyGroup = symbol { it.currencyGroup } ?: group,
            minusSign = symbol { it.minusSign } ?: "-",
            plusSign = symbol { it.plusSign } ?: "+",
            percentSign = symbol { it.percentSign } ?: "%",
            perMille = symbol { it.perMille } ?: "‰",
            approximatelySign = symbol { it.approximatelySign } ?: "~",
            exponential = symbol { it.exponential } ?: "E",
            superscriptingExponent = symbol { it.superscriptingExponent } ?: "×",
            infinity = symbol { it.infinity } ?: "∞",
            nan = symbol { it.nan } ?: "NaN",
            listSeparator = symbol { it.list } ?: ";",
            timeSeparator = symbol { it.timeSeparator } ?: ":",
            minimumGroupingDigits = partials.firstNotNullOfOrNull { it.minimumGroupingDigits } ?: 1,
        )
    }

    /** The capitalization bit field for [id], zero when nothing in the chain declares one. */
    fun resolveCapitalization(id: String): Int = flattener.dataChain(id).firstNotNullOfOrNull { partial(it).capitalization } ?: 0

    /** The plain decimal and percent patterns for [id]. */
    fun resolveNumberPatterns(id: String): ResolvedNumberPatterns {
        val partials = flattener.dataChain(id).map(::partial)
        val numberingSystem = partials.firstNotNullOfOrNull { it.defaultNumberingSystem } ?: "latn"
        val systemKeys = listOf(numberingSystem, "latn", "")

        fun lookUp(select: (PartialLocaleExtras) -> Map<String, String>): String? {
            for (key in systemKeys) {
                for (partial in partials) {
                    select(partial)[key]?.let { return it }
                }
            }
            return null
        }

        val decimal = lookUp { it.decimalPatterns } ?: error("$id: no decimal pattern after resolution")
        return ResolvedNumberPatterns(decimal, lookUp { it.percentPatterns } ?: "#,##0%")
    }

    /**
     * One compact table for [id], merged per key across the chain.
     *
     * Per key rather than per block: a locale that declares twelve of the
     * twenty-four patterns inherits the other twelve rather than replacing the
     * block wholesale.
     */
    fun resolveCompact(id: String, select: (PartialLocaleExtras) -> Map<String, MutableMap<String, String>>): Map<String, String> {
        val partials = flattener.dataChain(id).map(::partial)
        val numberingSystem = partials.firstNotNullOfOrNull { it.defaultNumberingSystem } ?: "latn"
        val merged = LinkedHashMap<String, String>()
        for (key in listOf(numberingSystem, "latn", "")) {
            for (partial in partials) {
                for ((entry, pattern) in select(partial)[key].orEmpty()) merged.putIfAbsent(entry, pattern)
            }
            if (merged.isNotEmpty()) break
        }
        return merged
    }
}
