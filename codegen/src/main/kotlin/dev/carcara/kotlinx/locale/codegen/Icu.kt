package dev.carcara.kotlinx.locale.codegen

import java.io.File

/**
 * Minimal parser for the ICU resource bundle text files under
 * icu4c/source/data/locales, covering the subset of syntax those files use:
 * nested tables, string values, string arrays, comments, and uXXXX escapes.
 */
sealed interface IcuValue
data class IcuString(val value: String) : IcuValue
data class IcuList(val values: List<String>) : IcuValue
data class IcuTable(val entries: Map<String, IcuValue>) : IcuValue
data object IcuOpaque : IcuValue

private class IcuTokenizer(private val text: String) {
    private var pos = 0

    sealed interface Token
    data object LBrace : Token
    data object RBrace : Token
    data object Comma : Token
    data class Str(val value: String) : Token
    data class Bare(val value: String) : Token
    data object Eof : Token

    private var pending: MutableList<Token> = ArrayList()

    fun peek(ahead: Int = 0): Token {
        while (pending.size <= ahead) pending.add(lex())
        return pending[ahead]
    }

    fun next(): Token = peek().also { pending.removeAt(0) }

    private fun lex(): Token {
        while (pos < text.length) {
            val ch = text[pos]
            when {
                ch.isWhitespace() || ch == '\uFEFF' -> pos++
                ch == '/' && pos + 1 < text.length && text[pos + 1] == '/' -> {
                    while (pos < text.length && text[pos] != '\n') pos++
                }
                else -> break
            }
        }
        if (pos >= text.length) return Eof
        return when (val ch = text[pos]) {
            '{' -> {
                pos++
                LBrace
            }
            '}' -> {
                pos++
                RBrace
            }
            ',' -> {
                pos++
                Comma
            }
            '"' -> Str(lexString())
            else -> {
                val start = pos
                while (pos < text.length && text[pos] !in "{},\"" && !text[pos].isWhitespace()) pos++
                check(pos > start) { "stuck at char '$ch' offset $pos" }
                Bare(text.substring(start, pos))
            }
        }
    }

    private fun lexString(): String {
        pos++ // opening quote
        val sb = StringBuilder()
        while (pos < text.length) {
            when (val ch = text[pos]) {
                '"' -> {
                    pos++
                    return sb.toString()
                }
                '\\' -> {
                    pos++
                    when (val esc = text[pos]) {
                        'u' -> {
                            sb.append(text.substring(pos + 1, pos + 5).toInt(16).toChar())
                            pos += 5
                        }
                        'n' -> {
                            sb.append('\n')
                            pos++
                        }
                        't' -> {
                            sb.append('\t')
                            pos++
                        }
                        else -> {
                            sb.append(esc)
                            pos++
                        }
                    }
                }
                else -> {
                    sb.append(ch)
                    pos++
                }
            }
        }
        error("unterminated string")
    }
}

fun parseIcuBundle(file: File): Pair<String, IcuTable> {
    val tokenizer = IcuTokenizer(file.readText())
    val rootKeyToken = tokenizer.next()
    val rootKey = (rootKeyToken as? IcuTokenizer.Bare)?.value
        ?: (rootKeyToken as? IcuTokenizer.Str)?.value
        ?: error("${file.name}: expected top-level key, got $rootKeyToken")
    check(tokenizer.next() is IcuTokenizer.LBrace) { "${file.name}: expected { after root key" }
    return rootKey.substringBefore(':') to parseTable(tokenizer, file.name)
}

private fun parseTable(tokenizer: IcuTokenizer, source: String): IcuTable {
    val entries = LinkedHashMap<String, IcuValue>()
    while (true) {
        when (val token = tokenizer.peek()) {
            is IcuTokenizer.RBrace -> {
                tokenizer.next()
                return IcuTable(entries)
            }
            is IcuTokenizer.Eof -> error("$source: unexpected EOF in table")
            is IcuTokenizer.Bare, is IcuTokenizer.Str -> {
                if (tokenizer.peek(1) is IcuTokenizer.LBrace) {
                    val rawKey = when (token) {
                        is IcuTokenizer.Bare -> token.value
                        is IcuTokenizer.Str -> token.value
                    }
                    tokenizer.next()
                    tokenizer.next() // key {
                    val key = rawKey.substringBefore(':')
                    val type = rawKey.substringAfter(':', "")
                    val value = parseValue(tokenizer, source)
                    entries[key] = if (type == "alias" || type == "bin" || type == "import") IcuOpaque else value
                } else {
                    // A stray scalar inside a table-like context (does not occur in
                    // locale bundles, but be tolerant).
                    tokenizer.next()
                }
            }
            is IcuTokenizer.Comma -> tokenizer.next()
            else -> error("$source: unexpected token $token")
        }
    }
}

private fun parseValue(tokenizer: IcuTokenizer, source: String): IcuValue {
    // After 'key {': decide between nested table and (list of) scalar values.
    val first = tokenizer.peek()
    if (first is IcuTokenizer.RBrace) {
        tokenizer.next()
        return IcuList(emptyList())
    }
    val isTable = (first is IcuTokenizer.Bare || first is IcuTokenizer.Str) &&
        tokenizer.peek(1) is IcuTokenizer.LBrace
    if (isTable) {
        return parseTable(tokenizer, source)
    }
    val items = ArrayList<String>()
    var sawComma = false
    var current: StringBuilder? = null
    while (true) {
        when (val token = tokenizer.next()) {
            is IcuTokenizer.RBrace -> {
                current?.let { items.add(it.toString()) }
                return if (items.size == 1 && !sawComma) IcuString(items[0]) else IcuList(items)
            }
            is IcuTokenizer.Str -> {
                // Adjacent strings concatenate; commas separate items.
                current = (current ?: StringBuilder()).append(token.value)
            }
            is IcuTokenizer.Bare -> current = (current ?: StringBuilder()).append(token.value)
            is IcuTokenizer.Comma -> {
                sawComma = true
                current?.let { items.add(it.toString()) }
                current = null
            }
            is IcuTokenizer.LBrace -> {
                // Anonymous nested array item, e.g. a (pattern, numbering system)
                // tuple inside DateTimePatterns; keep its first string.
                current?.let { items.add(it.toString()) }
                current = null
                when (val nested = parseValue(tokenizer, source)) {
                    is IcuString -> items.add(nested.value)
                    is IcuList -> nested.values.firstOrNull()?.let(items::add)
                    else -> {}
                }
            }
            else -> error("$source: unexpected token $token in value")
        }
    }
}

/** Resolves values across the ICU parent chain (per-path fallback, like ICU itself). */
class IcuResolver(private val localesDir: File) {
    private val cache = HashMap<String, IcuTable?>()

    private fun bundle(id: String): IcuTable? = cache.getOrPut(id) {
        val file = localesDir.resolve("$id.txt")
        if (file.exists()) parseIcuBundle(file).second else null
    }

    private fun parentOf(id: String, table: IcuTable?): String? {
        if (id == "root") return null
        (table?.entries?.get("%%Parent") as? IcuString)?.let { return it.value }
        val truncated = id.substringBeforeLast('_', "")
        return truncated.ifEmpty { "root" }
    }

    fun lookup(id: String, vararg path: String): IcuValue? {
        var current: String? = id
        while (current != null) {
            val table = bundle(current)
            if (table != null) {
                var value: IcuValue? = table
                for (segment in path) {
                    value = (value as? IcuTable)?.entries?.get(segment)
                    if (value == null) break
                }
                if (value != null && value != IcuOpaque) return value
            }
            current = parentOf(current, table)
        }
        return null
    }

    fun string(id: String, vararg path: String): String? = (lookup(id, *path) as? IcuString)?.value

    fun list(id: String, vararg path: String): List<String>? = (lookup(id, *path) as? IcuList)?.values
}

/**
 * Cross-checks the vendored ISO 4217 numeric codes against ICU's independently
 * maintained table, mirroring how ICU golden data cross-checks the CLDR parse.
 * Mismatches are warnings: the vendored official list stays authoritative.
 */
fun crossCheckCurrencyNumericCodes(iso4217: Iso4217Data, icuDir: File) {
    val file = icuDir.resolve("icu4c/source/data/misc/currencyNumericCodes.txt")
    if (!file.exists()) {
        println("[codegen] WARNING: $file not found, skipping ISO 4217 numeric code cross-check")
        return
    }
    val table = parseIcuBundle(file).second
    val codeMap = table.entries["codeMap"] as? IcuTable
        ?: error("${file.name}: missing codeMap table")
    var mismatches = 0
    for (currency in iso4217.currencies) {
        val icuNumeric = (codeMap.entries[currency.code] as? IcuString)?.value?.toIntOrNull()
        when {
            icuNumeric == null -> {
                println("[codegen] WARNING: ICU has no numeric code for ${currency.code}")
                mismatches++
            }
            currency.numericCode != icuNumeric -> {
                println(
                    "[codegen] WARNING: numeric code mismatch for ${currency.code}: " +
                        "ISO ${currency.numericCode}, ICU $icuNumeric",
                )
                mismatches++
            }
        }
    }
    println("[codegen] cross-checked ${iso4217.currencies.size} ISO 4217 numeric codes against ICU ($mismatches warnings)")
}

class IcuGoldenEntry(
    val tag: String,
    val dateFormats: List<String>?,
    val timeFormats: List<String>?,
    val glueFormats: List<String>?,
    val monthsWide: List<String>?,
    val monthsAbbr: List<String>?,
    val daysWide: List<String>?,
    val daysAbbr: List<String>?,
    /** The stand-alone context, which ICU stores under the same keys. */
    val monthsStandaloneWide: List<String>?,
    val monthsStandaloneAbbr: List<String>?,
    val daysStandaloneWide: List<String>?,
    val daysStandaloneAbbr: List<String>?,
    val am: String?,
    val pm: String?,
    /** Flexible day period names in [DAY_PERIOD_TYPES] order minus am/pm; null when ICU lacks the type. */
    val dayPeriods: List<String?>?,
)

val ICU_GOLDEN_LOCALES = listOf(
    "en", "en_GB", "en_AU", "de", "de_AT", "fr", "fr_CA", "es", "es_MX", "it",
    "pt", "pt_PT", "ja", "ko", "ru", "ar", "fi", "pl", "tr", "nl", "th",
    "zh", "zh_Hant", "hi", "cs", "sv", "uk", "he", "id", "vi",
)

/** Sunday-first ICU day arrays -> ISO Monday-first. */
private fun List<String>.sundayFirstToIso(): List<String> = List(7) { this[(it + 1) % 7] }

fun extractIcuGolden(icuDir: File): List<IcuGoldenEntry> {
    val localesDir = icuDir.resolve("icu4c/source/data/locales")
    val resolver = IcuResolver(localesDir)
    return ICU_GOLDEN_LOCALES.mapNotNull { id ->
        if (!localesDir.resolve("$id.txt").exists()) {
            println("[codegen] WARNING: ICU locale $id not found, skipping golden entry")
            return@mapNotNull null
        }
        val patterns = resolver.list(id, "calendar", "gregorian", "DateTimePatterns")
        if (patterns == null || patterns.size < 9) {
            println("[codegen] WARNING: ICU locale $id has no DateTimePatterns, skipping")
            return@mapNotNull null
        }
        val glue = if (patterns.size >= 13) patterns.subList(9, 13) else List(4) { patterns[8] }
        IcuGoldenEntry(
            tag = canonicalTag(id),
            dateFormats = patterns.subList(4, 8),
            timeFormats = patterns.subList(0, 4),
            glueFormats = glue,
            monthsWide = resolver.list(id, "calendar", "gregorian", "monthNames", "format", "wide"),
            monthsAbbr = resolver.list(id, "calendar", "gregorian", "monthNames", "format", "abbreviated"),
            daysWide = resolver.list(id, "calendar", "gregorian", "dayNames", "format", "wide")?.sundayFirstToIso(),
            daysAbbr = resolver.list(id, "calendar", "gregorian", "dayNames", "format", "abbreviated")?.sundayFirstToIso(),
            monthsStandaloneWide = resolver.list(id, "calendar", "gregorian", "monthNames", "stand-alone", "wide"),
            monthsStandaloneAbbr = resolver.list(id, "calendar", "gregorian", "monthNames", "stand-alone", "abbreviated"),
            daysStandaloneWide = resolver.list(id, "calendar", "gregorian", "dayNames", "stand-alone", "wide")
                ?.sundayFirstToIso(),
            daysStandaloneAbbr = resolver.list(id, "calendar", "gregorian", "dayNames", "stand-alone", "abbreviated")
                ?.sundayFirstToIso(),
            // AmPmMarkersAbbr is the abbreviated width, which is what the 'a'
            // pattern field (and our runtime data) uses.
            am = resolver.list(id, "calendar", "gregorian", "AmPmMarkersAbbr")?.getOrNull(0),
            pm = resolver.list(id, "calendar", "gregorian", "AmPmMarkersAbbr")?.getOrNull(1),
            dayPeriods = (resolver.lookup(id, "calendar", "gregorian", "dayPeriod", "format", "abbreviated") as? IcuTable)
                ?.let { table ->
                    DAY_PERIOD_TYPES.drop(2).map { type ->
                        (table.entries[type] as? IcuString)?.value
                    }
                },
        )
    }
}

fun emitIcuGolden(outputFile: File, icuTag: String, entries: List<IcuGoldenEntry>) {
    outputFile.parentFile.mkdirs()
    fun stringOrNull(value: String?): String = if (value == null) "null" else "\"${kotlinEscape(value)}\""
    fun listOrNull(values: List<String>?): String = if (values == null) {
        "null"
    } else {
        "listOf(${values.joinToString(", ") { "\"${kotlinEscape(it)}\"" }})"
    }
    fun nullableListOrNull(values: List<String?>?): String = if (values == null) {
        "null"
    } else {
        "listOf(${values.joinToString(", ", transform = ::stringOrNull)})"
    }

    outputFile.writeText(
        buildString {
            append("// GENERATED by :codegen from ICU $icuTag. Do not edit.\n")
            append("// Regenerate with: ./gradlew :codegen:generateLocaleData\n")
            append("package dev.carcara.kotlinx.locale.conformance\n\n")
            append("public const val ICU_DATETIME_GOLDEN_VERSION: String = \"${kotlinEscape(icuTag)}\"\n\n")
            append("public class IcuGolden(\n")
            append("    public val tag: String,\n")
            append("    public val dateFormats: List<String>?,\n")
            append("    public val timeFormats: List<String>?,\n")
            append("    public val glueFormats: List<String>?,\n")
            append("    public val monthsWide: List<String>?,\n")
            append("    public val monthsAbbr: List<String>?,\n")
            append("    public val daysWide: List<String>?,\n")
            append("    public val daysAbbr: List<String>?,\n")
            append("    public val monthsStandaloneWide: List<String>?,\n")
            append("    public val monthsStandaloneAbbr: List<String>?,\n")
            append("    public val daysStandaloneWide: List<String>?,\n")
            append("    public val daysStandaloneAbbr: List<String>?,\n")
            append("    public val am: String?,\n")
            append("    public val pm: String?,\n")
            append("    public val dayPeriods: List<String?>?,\n")
            append(")\n\n")
            append("public val icuGoldenData: List<IcuGolden> = listOf(\n")
            for (entry in entries) {
                append("    IcuGolden(\n")
                append("        tag = \"${kotlinEscape(entry.tag)}\",\n")
                append("        dateFormats = ${listOrNull(entry.dateFormats)},\n")
                append("        timeFormats = ${listOrNull(entry.timeFormats)},\n")
                append("        glueFormats = ${listOrNull(entry.glueFormats)},\n")
                append("        monthsWide = ${listOrNull(entry.monthsWide)},\n")
                append("        monthsAbbr = ${listOrNull(entry.monthsAbbr)},\n")
                append("        daysWide = ${listOrNull(entry.daysWide)},\n")
                append("        daysAbbr = ${listOrNull(entry.daysAbbr)},\n")
                append("        monthsStandaloneWide = ${listOrNull(entry.monthsStandaloneWide)},\n")
                append("        monthsStandaloneAbbr = ${listOrNull(entry.monthsStandaloneAbbr)},\n")
                append("        daysStandaloneWide = ${listOrNull(entry.daysStandaloneWide)},\n")
                append("        daysStandaloneAbbr = ${listOrNull(entry.daysStandaloneAbbr)},\n")
                append("        am = ${stringOrNull(entry.am)},\n")
                append("        pm = ${stringOrNull(entry.pm)},\n")
                append("        dayPeriods = ${nullableListOrNull(entry.dayPeriods)},\n")
                append("    ),\n")
            }
            append(")\n")
        },
    )
    println("[codegen] emitted ${entries.size} ICU golden entries to $outputFile")
}
