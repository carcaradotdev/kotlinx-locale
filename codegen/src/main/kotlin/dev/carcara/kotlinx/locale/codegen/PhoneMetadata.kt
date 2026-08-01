package dev.carcara.kotlinx.locale.codegen

import java.io.File

/**
 * Google's phone metadata, parsed from `PhoneNumberMetadata.xml`.
 *
 * The shape follows `BuildMetadataFromXml.java` rather than the XML, because
 * the XML leaves several things to the builder: a description with no pattern of
 * its own is absent rather than empty, possible lengths are merged up from
 * `generalDesc` and deduplicated, and a territory with no `availableFormats`
 * inherits the formats of the main territory for its calling code.
 *
 * ## Why this can be pure common Kotlin
 *
 * The obvious objection to a multiplatform port is that libphonenumber
 * validates with regular expressions and Kotlin's `Regex` delegates to a
 * different engine on every target, so "the same number" could validate
 * differently on Android and on JS. That objection dissolves once you look at
 * what the patterns actually contain. Across all 2292 of them the constructs
 * are: alternation, character classes with ranges, `\d`, non-capturing groups,
 * bounded repetition, the optional marker, and an end-of-input `$` that occurs
 * only in the national-prefix rules. No backreferences, no lookaround, no
 * unbounded quantifiers, no dot. The format patterns are smaller still:
 * capturing groups of digit runs and nothing else.
 *
 * That is a subset a few hundred lines of common Kotlin can evaluate exactly,
 * which is what `-phone-metadata-runtime` does. [assertSupportedConstructs]
 * fails generation if a later libphonenumber release introduces anything
 * outside it, so the subset cannot silently stop being enough. It is the same
 * arrangement the RBNF ordinal rules use, and for the same reason: a bounded
 * evaluator that is identical everywhere beats a host one that is not.
 */

/** The kinds of number libphonenumber's metadata describes. */
enum class PhoneDescType(val element: String) {
    FIXED_LINE("fixedLine"),
    MOBILE("mobile"),
    TOLL_FREE("tollFree"),
    PREMIUM_RATE("premiumRate"),
    SHARED_COST("sharedCost"),
    PERSONAL_NUMBER("personalNumber"),
    VOIP("voip"),
    PAGER("pager"),
    UAN("uan"),
    VOICEMAIL("voicemail"),
    NO_INTERNATIONAL_DIALLING("noInternationalDialling"),
    ;

    companion object {
        /** The order the runtime tests types in, which decides a tie. */
        val TESTED: List<PhoneDescType> = listOf(
            PREMIUM_RATE,
            TOLL_FREE,
            SHARED_COST,
            VOIP,
            PERSONAL_NUMBER,
            PAGER,
            UAN,
            VOICEMAIL,
        )
    }
}

class PhoneDesc(
    /** `null` when the territory declares no pattern for this type, which means it has none. */
    val nationalNumberPattern: String?,
    val possibleLengths: List<Int>,
    val localOnlyLengths: List<Int>,
    val exampleNumber: String?,
)

class PhoneNumberFormat(
    val pattern: String,
    val format: String,
    val leadingDigits: List<String>,
    val nationalPrefixFormattingRule: String?,
    val nationalPrefixOptionalWhenFormatting: Boolean,
    val domesticCarrierCodeFormattingRule: String?,
    /** The international form, when it differs; `"NA"` means the format is not used abroad. */
    val internationalFormat: String?,
)

class PhoneTerritory(
    val id: String,
    val countryCode: Int,
    val mainCountryForCode: Boolean,
    val leadingDigits: String?,
    val internationalPrefix: String?,
    val preferredInternationalPrefix: String?,
    val nationalPrefix: String?,
    val nationalPrefixForParsing: String?,
    val nationalPrefixTransformRule: String?,
    val preferredExtnPrefix: String?,
    val mobileNumberPortableRegion: Boolean,
    val generalDesc: PhoneDesc,
    val descriptions: Map<PhoneDescType, PhoneDesc>,
    val formats: List<PhoneNumberFormat>,
)

class PhoneMetadata(val territories: List<PhoneTerritory>) {

    /** Calling code to the territory ids that use it, main country first. */
    val territoriesByCode: Map<Int, List<PhoneTerritory>> = territories
        .groupBy { it.countryCode }
        .mapValues { (_, list) -> list.sortedByDescending { it.mainCountryForCode } }
}

fun parsePhoneMetadata(repoDir: File): PhoneMetadata {
    val file = repoDir.resolve("resources/PhoneNumberMetadata.xml")
    check(file.isFile) { "${file.path} is missing; widen PHONE_REPO.sparsePaths" }
    val root = parseXml(file).documentElement
    val territories = root.childElements("territories").firstOrNull()
        ?: error("PhoneNumberMetadata.xml: no <territories>")

    val parsed = territories.childElements("territory").map(::parseTerritory)
    check(parsed.size > 200) { "implausibly few territories (${parsed.size})" }

    // A territory with no formats of its own uses the main country's for its
    // calling code, which is how Jersey and Guernsey format like Great Britain.
    val mainByCode = parsed.filter { it.mainCountryForCode }.associateBy { it.countryCode }
    val resolved = parsed.map { territory ->
        if (territory.formats.isNotEmpty()) return@map territory
        val main = mainByCode[territory.countryCode] ?: return@map territory
        if (main.id == territory.id) territory else territory.withFormats(main.formats)
    }

    val metadata = PhoneMetadata(resolved)
    assertSupportedConstructs(metadata)
    println(
        "[codegen] libphonenumber ${PHONE_REPO.tag}: ${resolved.size} territories, " +
            "${resolved.map { it.countryCode }.distinct().size} calling codes, " +
            "${resolved.sumOf { it.formats.size }} number formats",
    )
    return metadata
}

private fun PhoneTerritory.withFormats(formats: List<PhoneNumberFormat>) = PhoneTerritory(
    id, countryCode, mainCountryForCode, leadingDigits, internationalPrefix, preferredInternationalPrefix,
    nationalPrefix, nationalPrefixForParsing, nationalPrefixTransformRule, preferredExtnPrefix,
    mobileNumberPortableRegion, generalDesc, descriptions, formats,
)

private fun parseTerritory(el: org.w3c.dom.Element): PhoneTerritory {
    fun attr(name: String): String? = el.getAttribute(name).takeIf { it.isNotEmpty() }

    // Declared first, resolved second. The general description carries no
    // lengths of its own in the XML: the builder computes them as the union of
    // the typed ones, and then a type whose lengths equal that union stores
    // nothing and reads them back from it. Resolving in one pass would make the
    // union depend on values the union is supposed to produce.
    val declared = LinkedHashMap<PhoneDescType, PhoneDesc>()
    for (type in PhoneDescType.entries) {
        val child = el.childElements(type.element).firstOrNull() ?: continue
        declared[type] = parseDesc(child, null)
    }

    // `noInternationalDialling` is excluded, as it is in the builder: it names
    // numbers that exist and cannot be dialled from abroad, not a length the
    // territory otherwise lacks.
    val typed = declared.filterKeys { it != PhoneDescType.NO_INTERNATIONAL_DIALLING }.values
    val generalPattern = el.childElements("generalDesc").firstOrNull()
        ?.childElements("nationalNumberPattern")?.firstOrNull()
        ?.textContent?.let(::stripWhitespace)?.takeIf { it.isNotEmpty() }
    val generalDesc = PhoneDesc(
        nationalNumberPattern = generalPattern,
        possibleLengths = typed.flatMap { it.possibleLengths }.distinct().sorted(),
        localOnlyLengths = typed.flatMap { it.localOnlyLengths }.distinct().sorted(),
        exampleNumber = null,
    )

    val descriptions = declared.mapValues { (_, desc) ->
        if (desc.possibleLengths.isNotEmpty()) {
            desc
        } else {
            PhoneDesc(desc.nationalNumberPattern, generalDesc.possibleLengths, desc.localOnlyLengths, desc.exampleNumber)
        }
    }

    val nationalPrefix = attr("nationalPrefix")
    return PhoneTerritory(
        id = el.getAttribute("id"),
        countryCode = el.getAttribute("countryCode").toInt(),
        mainCountryForCode = attr("mainCountryForCode") == "true",
        leadingDigits = attr("leadingDigits")?.let(::stripWhitespace),
        internationalPrefix = attr("internationalPrefix"),
        preferredInternationalPrefix = attr("preferredInternationalPrefix"),
        nationalPrefix = nationalPrefix,
        // The builder defaults this to the national prefix itself, which is what
        // makes a plain "0" strippable without the XML repeating it 144 times.
        nationalPrefixForParsing = attr("nationalPrefixForParsing")?.let(::stripWhitespace) ?: nationalPrefix,
        nationalPrefixTransformRule = attr("nationalPrefixTransformRule"),
        preferredExtnPrefix = attr("preferredExtnPrefix"),
        mobileNumberPortableRegion = attr("mobileNumberPortableRegion") == "true",
        generalDesc = generalDesc,
        descriptions = descriptions,
        formats = el.childElements("availableFormats").firstOrNull()
            ?.childElements("numberFormat")
            ?.map { parseFormat(it, nationalPrefix) }
            .orEmpty(),
    )
}

private fun parseDesc(el: org.w3c.dom.Element, general: PhoneDesc?): PhoneDesc {
    val pattern = el.childElements("nationalNumberPattern").firstOrNull()
        ?.textContent?.let(::stripWhitespace)?.takeIf { it.isNotEmpty() }
    val lengths = el.childElements("possibleLengths").firstOrNull()
    val national = parseLengths(lengths?.getAttribute("national"))
    val localOnly = parseLengths(lengths?.getAttribute("localOnly"))
    return PhoneDesc(
        nationalNumberPattern = pattern,
        possibleLengths = national.ifEmpty { general?.possibleLengths.orEmpty() },
        localOnlyLengths = localOnly,
        exampleNumber = el.childElements("exampleNumber").firstOrNull()?.textContent?.let(::stripWhitespace),
    )
}

/**
 * `"6,9"` and `"[5-12]"` as the lengths they stand for.
 *
 * The bracket form is a range rather than a character class, which is a trap
 * worth naming: `[5-12]` is five through twelve, not the three characters `5`,
 * `-` and `12`.
 */
private fun parseLengths(spec: String?): List<Int> {
    if (spec.isNullOrEmpty()) return emptyList()
    val lengths = sortedSetOf<Int>()
    for (part in spec.split(',')) {
        val trimmed = part.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val (from, to) = trimmed.removeSurrounding("[", "]").split('-', limit = 2)
            for (value in from.toInt()..to.toInt()) lengths += value
        } else if (trimmed.isNotEmpty()) {
            lengths += trimmed.toInt()
        }
    }
    return lengths.toList()
}

private fun parseFormat(el: org.w3c.dom.Element, nationalPrefix: String?): PhoneNumberFormat {
    // Trimmed rather than stripped. A pattern can be wrapped across lines
    // because none of its whitespace means anything; a format string's spaces
    // are the grouping it exists to describe, so `$1 $2` must not become `$1$2`.
    val intl = el.childElements("intlFormat").firstOrNull()?.textContent?.trim()
    return PhoneNumberFormat(
        pattern = stripWhitespace(el.getAttribute("pattern")),
        format = el.childElements("format").firstOrNull()?.textContent?.trim().orEmpty(),
        leadingDigits = el.childElements("leadingDigits").map { stripWhitespace(it.textContent) },
        // `$NP` and `$FG` are placeholders the builder expands at build time, so
        // the runtime never sees them.
        // The rule carries its own spacing too: `$NP $FG` is a prefix, a space
        // and the rest, and `$NP$FG` is the same two parts run together.
        nationalPrefixFormattingRule = el.getAttribute("nationalPrefixFormattingRule")
            .takeIf { it.isNotEmpty() }
            ?.replace("\$NP", nationalPrefix.orEmpty())
            ?.replace("\$FG", "\$1"),
        nationalPrefixOptionalWhenFormatting =
        el.getAttribute("nationalPrefixOptionalWhenFormatting") == "true",
        domesticCarrierCodeFormattingRule = el.getAttribute("carrierCodeFormattingRule").takeIf { it.isNotEmpty() },
        internationalFormat = intl,
    )
}

/** The XML wraps patterns across lines for readability; none of it is significant. */
private fun stripWhitespace(text: String): String = text.filterNot(Char::isWhitespace)

/**
 * Fails generation if any pattern uses a construct the runtime matcher does not
 * implement.
 *
 * The whole multiplatform story rests on the claim that this metadata needs only
 * a bounded subset of regular expressions, so the claim is checked rather than
 * asserted. A libphonenumber release that starts using a backreference or a
 * lookahead breaks the build here, with the pattern quoted, instead of silently
 * validating the wrong numbers on one target.
 */
private fun assertSupportedConstructs(metadata: PhoneMetadata) {
    val unsupported = LinkedHashMap<String, MutableSet<String>>()
    fun check(source: String, pattern: String?, capturing: Boolean) {
        if (pattern.isNullOrEmpty()) return
        for (construct in unsupportedConstructsIn(pattern, capturing)) {
            unsupported.getOrPut(construct) { LinkedHashSet() } += "$source: $pattern"
        }
    }

    for (territory in metadata.territories) {
        val where = territory.id
        check(where, territory.generalDesc.nationalNumberPattern, capturing = false)
        for ((type, desc) in territory.descriptions) check("$where/$type", desc.nationalNumberPattern, false)
        check("$where/leadingDigits", territory.leadingDigits, capturing = false)
        check("$where/nationalPrefixForParsing", territory.nationalPrefixForParsing, capturing = true)
        for (format in territory.formats) {
            check("$where/format", format.pattern, capturing = true)
            for (digits in format.leadingDigits) check("$where/format/leadingDigits", digits, capturing = false)
        }
    }

    check(unsupported.isEmpty()) {
        buildString {
            append("libphonenumber ${PHONE_REPO.tag} uses regex constructs the bounded matcher does not implement.\n")
            append("Either extend kotlinx-locale-phone-metadata-runtime's matcher to cover them, or pin an\n")
            append("earlier release. Do not fall back to the host regex engine: its behaviour differs per\n")
            append("Kotlin target, which is the thing this subset exists to avoid.\n")
            for ((construct, examples) in unsupported) {
                append("  $construct (${examples.size} patterns), e.g. ${examples.first()}\n")
            }
        }
    }
    println("[codegen] phone patterns use only the bounded regex subset the matcher implements")
}

/** The constructs in [pattern] that the runtime matcher cannot evaluate. */
private fun unsupportedConstructsIn(pattern: String, capturing: Boolean): List<String> {
    val found = ArrayList<String>()
    var index = 0
    while (index < pattern.length) {
        when {
            pattern.startsWith("(?:", index) -> index += 3
            pattern.startsWith("(?", index) -> {
                found += "extended group (?${pattern.getOrNull(index + 2)}"
                index += 3
            }
            pattern[index] == '(' -> {
                if (!capturing) found += "capturing group in a matching-only pattern"
                index++
            }
            pattern[index] == '\\' -> {
                val next = pattern.getOrNull(index + 1)
                if (next != 'd') found += "escape \\$next"
                index += 2
            }
            pattern[index] == '[' -> {
                val close = pattern.indexOf(']', index)
                if (close < 0) {
                    found += "unterminated character class"
                    index = pattern.length
                } else {
                    val body = pattern.substring(index + 1, close)
                    if (body.startsWith('^')) found += "negated character class"
                    if ('\\' in body) found += "escape inside a character class"
                    index = close + 1
                }
            }
            pattern[index] == '{' -> {
                val close = pattern.indexOf('}', index)
                if (close < 0 || !BOUNDED_QUANTIFIER.matches(pattern.substring(index, close + 1))) {
                    found += "unbounded or malformed quantifier"
                }
                index = if (close < 0) pattern.length else close + 1
            }
            pattern[index] in "*+" -> {
                found += "unbounded quantifier ${pattern[index]}"
                index++
            }
            pattern[index] == '.' -> {
                found += "dot"
                index++
            }
            // `$` asserts the end of input and appears only in the
            // national-prefix rules, where it is what makes Antigua's
            // `([457]\d{6})$|1` strip a seven-digit local number only when
            // nothing follows it. The matcher implements it as a position test.
            // `^` never appears: these patterns are all applied from the start
            // already.
            pattern[index] == '$' -> index++
            pattern[index] == '^' -> {
                found += "anchor ^"
                index++
            }
            else -> index++
        }
    }
    return found.distinct()
}

private val BOUNDED_QUANTIFIER = Regex("""\{\d+(,\d+)?}""")
