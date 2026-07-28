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

    var defaultNumberingSystem: String? = null
    var minimumGroupingDigits: Int? = null

    /** numbering system attribute value ("" when absent) -> declared symbols. */
    val symbols = LinkedHashMap<String, PartialNumberSymbols>()

    /** numbering system attribute value ("" when absent) -> declared currency patterns. */
    val currencyPatterns = LinkedHashMap<String, PartialCurrencyPatterns>()
}

class PartialNumberSymbols {
    var decimal: String? = null
    var group: String? = null
    var currencyDecimal: String? = null
    var currencyGroup: String? = null
    var minusSign: String? = null
}

class PartialCurrencyPatterns {
    var standard: String? = null
    var standardAlpha: String? = null
    var accounting: String? = null
    var accountingAlpha: String? = null
}

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

    ldml.child("localeDisplayNames")?.child("territories")?.let { territories ->
        for (territory in territories.childElements("territory")) {
            if (territory.hasAttribute("alt")) continue
            val type = territory.getAttribute("type")
            if (type !in countryCodes) continue
            val name = territory.textContent.cleaned() ?: continue
            extras.territoryNames.putIfAbsent(type, name)
        }
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
    }

    for (formatsEl in numbers.childElements("currencyFormats")) {
        val target = extras.currencyPatterns.getOrPut(formatsEl.getAttribute("numberSystem")) { PartialCurrencyPatterns() }
        // Only the default (type-less) length; type="short" is compact notation.
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
}
