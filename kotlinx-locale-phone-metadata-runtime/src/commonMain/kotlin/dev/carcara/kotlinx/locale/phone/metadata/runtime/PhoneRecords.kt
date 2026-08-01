@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.phone.metadata.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.KEY_SEPARATOR
import dev.carcara.kotlinx.locale.phone.PhoneNumberType

/**
 * One territory's numbering plan, decoded from its packed record.
 *
 * Patterns compile on first use rather than at construction: a validity check
 * touches one territory and two or three of its descriptions, so compiling all
 * 254 territories up front would do two hundred times the work for the same
 * answer. Compiled patterns are then kept, because the second call is usually
 * for the same territory as the first.
 */
@InternalKotlinxLocaleApi
public class PhoneTerritoryRecord(record: String) {

    private val fields = record.split(FIELD_SEPARATOR)

    public val id: String = fields[0]
    public val callingCode: Int = fields[1].toInt()
    public val isMainCountryForCode: Boolean = 'm' in fields[2]
    public val isMobileNumberPortable: Boolean = 'p' in fields[2]
    public val leadingDigits: String? = fields[3].takeIf(String::isNotEmpty)
    public val internationalPrefix: String? = fields[4].takeIf(String::isNotEmpty)
    public val preferredInternationalPrefix: String? = fields[5].takeIf(String::isNotEmpty)
    public val nationalPrefix: String? = fields[6].takeIf(String::isNotEmpty)
    public val nationalPrefixForParsing: String? = fields[7].takeIf(String::isNotEmpty)
    public val nationalPrefixTransformRule: String? = fields[8].takeIf(String::isNotEmpty)
    public val preferredExtensionPrefix: String? = fields[9].takeIf(String::isNotEmpty)

    /** The whole-territory description, which is the cheap reject before the typed ones. */
    public val generalDescription: PhoneDescription =
        PhoneDescription(fields[10].takeIf(String::isNotEmpty), fields[11], "", "")

    private val descriptions: Map<PhoneNumberType, PhoneDescription> = buildMap {
        for (index in DESCRIPTION_FIELD_START until fields.size) {
            val parts = fields[index].split(KEY_SEPARATOR)
            if (parts.size < 5) continue
            val type = descriptionType(parts[0]) ?: continue
            put(type, PhoneDescription(parts[1].takeIf(String::isNotEmpty), parts[2], parts[3], parts[4]))
        }
    }

    private var parsingPattern: DigitPattern? = null
    private var leadingDigitsPattern: DigitPattern? = null

    /** The description for [type], or `null` when this territory has no such numbers. */
    public fun descriptionOrNull(type: PhoneNumberType): PhoneDescription? = descriptions[type]

    /** Every type this territory declares, in metadata order. */
    public val declaredTypes: Set<PhoneNumberType> get() = descriptions.keys

    /** The compiled national-prefix rule, or `null` when the territory has none. */
    public fun nationalPrefixPattern(): DigitPattern? {
        parsingPattern?.let { return it }
        val source = nationalPrefixForParsing ?: return null
        return DigitPattern.parse(source).also { parsingPattern = it }
    }

    /** The compiled `leadingDigits`, which is what picks between territories sharing a code. */
    public fun leadingDigitsPattern(): DigitPattern? {
        leadingDigitsPattern?.let { return it }
        val source = leadingDigits ?: return null
        return DigitPattern.parse(source).also { leadingDigitsPattern = it }
    }

    private companion object {
        /** Fields 0 through 11 are scalars; the descriptions start after them. */
        const val DESCRIPTION_FIELD_START = 12

        fun descriptionType(name: String): PhoneNumberType? = when (name) {
            "FIXED_LINE" -> PhoneNumberType.FIXED_LINE
            "MOBILE" -> PhoneNumberType.MOBILE
            "TOLL_FREE" -> PhoneNumberType.TOLL_FREE
            "PREMIUM_RATE" -> PhoneNumberType.PREMIUM_RATE
            "SHARED_COST" -> PhoneNumberType.SHARED_COST
            "PERSONAL_NUMBER" -> PhoneNumberType.PERSONAL_NUMBER
            "VOIP" -> PhoneNumberType.VOIP
            "PAGER" -> PhoneNumberType.PAGER
            "UAN" -> PhoneNumberType.UAN
            "VOICEMAIL" -> PhoneNumberType.VOICEMAIL
            // Not a type a number can be reported as: it names numbers that
            // exist and cannot be reached from abroad, which is a property of
            // the call rather than of the number.
            else -> null
        }
    }
}

/** One kind of number within a territory: the pattern it matches and the lengths it takes. */
@InternalKotlinxLocaleApi
public class PhoneDescription(
    private val patternSource: String?,
    lengths: String,
    localOnly: String,
    /** A number of this kind that is valid, for placeholders and tests. */
    public val exampleNumber: String,
) {

    /** The lengths a number of this kind may have, ascending. */
    public val possibleLengths: List<Int> = parseLengths(lengths)

    /** Lengths valid only when dialled within the same area. */
    public val localOnlyLengths: List<Int> = parseLengths(localOnly)

    private var compiled: DigitPattern? = null

    /** True when this description declares a pattern at all. */
    public val hasPattern: Boolean get() = patternSource != null

    /** True when [nationalNumber] matches this description whole. */
    public fun matches(nationalNumber: String): Boolean {
        val source = patternSource ?: return false
        // The length check first: it is a comparison against a short list where
        // the pattern match is a walk over a parse tree, and it rejects most of
        // what reaches here.
        if (possibleLengths.isNotEmpty() && nationalNumber.length !in possibleLengths) return false
        val pattern = compiled ?: DigitPattern.parse(source).also { compiled = it }
        return pattern.matches(nationalNumber)
    }

    /** Whether [length] is one this description accepts, ignoring the pattern. */
    public fun acceptsLength(length: Int): Boolean = length in possibleLengths || length in localOnlyLengths

    private companion object {
        fun parseLengths(spec: String): List<Int> = if (spec.isEmpty()) emptyList() else spec.split(',').mapNotNull { it.toIntOrNull() }
    }
}

/** One territory's number formats, decoded from its packed record. */
@InternalKotlinxLocaleApi
public class PhoneFormatRecord(record: String) {

    private val fields = record.split(FIELD_SEPARATOR)

    public val territoryId: String = fields[0]

    public val formats: List<PhoneFormatRule> =
        (1 until fields.size).mapNotNull { index ->
            val parts = fields[index].split(KEY_SEPARATOR)
            if (parts.size < 7) null else PhoneFormatRule(parts)
        }
}

/** One `numberFormat`: which numbers it applies to, and what it writes. */
@InternalKotlinxLocaleApi
public class PhoneFormatRule(parts: List<String>) {

    private val patternSource: String = parts[0]

    /** `$1 $2 $3`, with the groups the pattern captured. */
    public val format: String = parts[1]

    private val leadingDigitsSources: List<String> =
        parts[2].split(' ').filter(String::isNotEmpty)

    /** How the national prefix joins the rest, or `null` when it just precedes it. */
    public val nationalPrefixFormattingRule: String? = parts[3].takeIf(String::isNotEmpty)

    /** True when the national form may be written without the prefix. */
    public val nationalPrefixOptional: Boolean = parts[4] == "1"

    /** The international form, when it differs. `NA` means this format is not used abroad. */
    public val internationalFormat: String? = parts[5].takeIf(String::isNotEmpty)

    public val carrierCodeFormattingRule: String? = parts[6].takeIf(String::isNotEmpty)

    /** The pattern's source, which the as-you-type formatter reads runs out of. */
    internal val patternText: String get() = patternSource

    /** Memoised digit runs; empty means "computed, and not usable". */
    internal var cachedRuns: List<Int>? = null

    private var compiled: DigitPattern? = null
    private var leadingDigits: List<DigitPattern>? = null

    /** The compiled pattern, whose capture groups the format string writes back. */
    public fun pattern(): DigitPattern = compiled ?: DigitPattern.parse(patternSource).also { compiled = it }

    /**
     * True when this rule is the one for [nationalNumber].
     *
     * The last `leadingDigits` is the most specific and is the one libphonenumber
     * tests, which is why a rule listing `800`, `8001` and `80011` is selected on
     * `80011` alone: the shorter ones are there for the as-you-type formatter,
     * which asks the same question with fewer digits in hand.
     */
    public fun appliesTo(nationalNumber: String): Boolean {
        val patterns = leadingDigits ?: leadingDigitsSources.map(DigitPattern::parse).also { leadingDigits = it }
        val mostSpecific = patterns.lastOrNull() ?: return pattern().matches(nationalNumber)
        return mostSpecific.prefixLength(nationalNumber) >= 0 && pattern().matches(nationalNumber)
    }

    /** True when the rule's [index]th leading-digit pattern accepts [prefix], for as-you-type. */
    public fun acceptsPrefix(prefix: String, index: Int): Boolean {
        val patterns = leadingDigits ?: leadingDigitsSources.map(DigitPattern::parse).also { leadingDigits = it }
        if (patterns.isEmpty()) return true
        val pattern = patterns.getOrNull(minOf(index, patterns.size - 1)) ?: return true
        return pattern.prefixLength(prefix) >= 0
    }

    /** How many leading-digit patterns this rule declares. */
    public val leadingDigitsCount: Int get() = leadingDigitsSources.size
}
