@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.phone.metadata.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.forAlpha2OrNull
import dev.carcara.kotlinx.locale.phone.PhoneNumber
import dev.carcara.kotlinx.locale.phone.PhoneNumberFormat
import dev.carcara.kotlinx.locale.phone.PhoneNumberSource
import dev.carcara.kotlinx.locale.phone.PhoneNumberType
import dev.carcara.kotlinx.locale.phone.PhoneParseFailure
import dev.carcara.kotlinx.locale.phone.PhoneParseResult

/** The shortest and longest a national number may be, per E.164. */
private const val MIN_NATIONAL_LENGTH = 2
private const val MAX_NATIONAL_LENGTH = 17

/** A calling code is one, two or three digits. */
private const val MAX_CALLING_CODE_DIGITS = 3

/**
 * A [PhoneNumberSource] over the generated tables.
 *
 * The algorithms follow `PhoneNumberUtil.java`, which is the reference for
 * everything the XML does not describe: which end of a shared calling code wins,
 * what a national prefix does when the number without it would also be valid,
 * and the order the type descriptions are tested in. Where this differs from the
 * reference it is in representation rather than behaviour, and the conformance
 * fixture is what says so.
 */
public class PayloadPhoneNumbers(
    /** Territory key to its packed record; see the emitter for why the key is not always a country. */
    territories: Map<String, String>,
    formats: Map<String, String> = emptyMap(),
) : PhoneNumberSource {

    // Decoded eagerly, compiled lazily. Decoding is a string split per
    // territory and the map has to exist before the first lookup; compiling the
    // patterns is the expensive half and waits until a number needs one.
    private val byId: Map<String, PhoneTerritoryRecord> =
        territories.mapValues { (_, record) -> PhoneTerritoryRecord(record) }

    private val byCallingCode: Map<Int, List<PhoneTerritoryRecord>> =
        byId.values.groupBy { it.callingCode }
            .mapValues { (_, list) -> list.sortedByDescending { it.isMainCountryForCode } }

    private val formatsById: Map<String, PhoneFormatRecord> =
        formats.mapValues { (_, record) -> PhoneFormatRecord(record) }

    override val supportedRegions: Set<Country> =
        byId.keys.mapNotNullTo(LinkedHashSet()) { Country.forAlpha2OrNull(it) }

    override fun callingCodeOrNull(region: Country): Int? = byId[region.name]?.callingCode

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    override fun parse(text: String, defaultRegion: Country?): PhoneParseResult {
        val (body, extension) = splitExtension(text)
        val digitsAndPlus = keepDialable(body)
        if (digitsAndPlus.none(Char::isDigit)) return failed(PhoneParseFailure.NOT_A_NUMBER)

        val region = defaultRegion?.let { byId[it.name] }
        val stripped = stripInternationalPrefix(digitsAndPlus, region)

        var callingCode: Int
        var national: String
        if (stripped.hadPrefix) {
            callingCode = readCallingCode(stripped.digits) ?: return failed(PhoneParseFailure.UNKNOWN_CALLING_CODE)
            national = stripped.digits.substring(callingCodeLength(callingCode))
        } else {
            if (region == null) return failed(PhoneParseFailure.MISSING_REGION)
            // No `+` and no international prefix, so the digits are national.
            // libphonenumber still tries the region's own calling code as a
            // prefix, because people write their own number both ways and a
            // bare `44 121…` in the United Kingdom is the international form
            // without its plus.
            callingCode = region.callingCode
            national = stripCountryCodeWrittenBare(stripped.digits, region)
        }

        national = stripNationalPrefix(national, callingCode)
        return when {
            national.length < MIN_NATIONAL_LENGTH -> failed(PhoneParseFailure.TOO_SHORT)
            national.length > MAX_NATIONAL_LENGTH -> failed(PhoneParseFailure.TOO_LONG)
            else -> PhoneParseResult.Parsed(PhoneNumber(callingCode, national, extension))
        }
    }

    private fun failed(reason: PhoneParseFailure) = PhoneParseResult.Failed(reason)

    /**
     * The input split at its extension marker.
     *
     * The markers are the ones people write rather than a specification: RFC
     * 3966's `;ext=`, the words `ext`, `extn` and `x`, and a bare `#` at the end.
     */
    private fun splitExtension(text: String): Pair<String, String?> {
        val lower = text.lowercase()
        for (marker in EXTENSION_MARKERS) {
            val at = lower.indexOf(marker)
            if (at < 0) continue
            val tail = text.substring(at + marker.length).filter(Char::isDigit)
            if (tail.isNotEmpty()) return text.substring(0, at) to tail
        }
        // A trailing `#` is an extension only when digits follow it.
        val hash = text.lastIndexOf('#')
        if (hash > 0) {
            val tail = text.substring(hash + 1).filter(Char::isDigit)
            if (tail.isNotEmpty()) return text.substring(0, hash) to tail
        }
        return text to null
    }

    /** The input reduced to digits and a leading plus, which is all that carries meaning. */
    private fun keepDialable(text: String): String = buildString(text.length) {
        for ((index, ch) in text.withIndex()) {
            when {
                ch.isDigit() -> append(ch)
                (ch == '+' || ch == '＋') && isEmpty() -> append('+')
                // A plus after the first digit is not a plus; some inputs carry
                // one from a paste and it is noise.
                index >= 0 -> Unit
            }
        }
    }

    private class Stripped(val digits: String, val hadPrefix: Boolean)

    /** The digits with any `+` or international dialling prefix removed. */
    private fun stripInternationalPrefix(input: String, region: PhoneTerritoryRecord?): Stripped {
        if (input.startsWith('+')) return Stripped(input.substring(1), hadPrefix = true)
        val idd = region?.internationalPrefix ?: return Stripped(input, hadPrefix = false)
        val pattern = runCatching { DigitPattern.parse(idd) }.getOrNull() ?: return Stripped(input, false)
        val length = pattern.prefixLength(input)
        if (length <= 0 || length >= input.length) return Stripped(input, hadPrefix = false)
        // A stripped prefix that leaves a leading zero was not a prefix: no
        // calling code starts with zero, so the digits were national all along.
        val rest = input.substring(length)
        return if (rest.startsWith('0')) Stripped(input, false) else Stripped(rest, hadPrefix = true)
    }

    /** The calling code at the front of [digits], or `null` when none is known. */
    private fun readCallingCode(digits: String): Int? {
        if (digits.startsWith('0')) return null
        for (length in 1..MAX_CALLING_CODE_DIGITS) {
            if (length > digits.length) break
            val candidate = digits.substring(0, length).toIntOrNull() ?: continue
            if (candidate in byCallingCode) return candidate
        }
        return null
    }

    private fun callingCodeLength(callingCode: Int): Int = callingCode.toString().length

    /**
     * The national number when the input carried its own calling code without a
     * plus.
     *
     * Only accepted when dropping the code leaves something the territory calls
     * valid and keeping it does not, so a national number that happens to start
     * with its own country's digits is left alone.
     */
    private fun stripCountryCodeWrittenBare(digits: String, region: PhoneTerritoryRecord): String {
        val code = region.callingCode.toString()
        if (!digits.startsWith(code) || digits.length <= code.length) return digits
        val without = digits.substring(code.length)
        val general = region.generalDescription
        val keptIsValid = general.matches(digits)
        val strippedIsValid = general.matches(stripNationalPrefix(without, region.callingCode))
        return if (!keptIsValid && strippedIsValid) without else digits
    }

    /**
     * [national] with the territory's national prefix removed.
     *
     * Removed only when what remains is still something the territory
     * recognises. A national prefix is a dialling convenience rather than part
     * of the number, but the same digits can begin a valid number, and dropping
     * them there would turn a good number into a bad one.
     */
    private fun stripNationalPrefix(national: String, callingCode: Int): String {
        val region = regionRecordFor(callingCode, national) ?: return national
        val pattern = region.nationalPrefixPattern() ?: return national
        val general = region.generalDescription
        val captured = pattern.capturePrefix(national) ?: return national
        val (groups, end) = captured
        if (end <= 0) return national

        val transform = region.nationalPrefixTransformRule
        val candidate = if (transform != null && groups.isNotEmpty() && groups[0] != null) {
            applyGroups(transform, groups)
        } else {
            national.substring(end)
        }
        if (candidate.isEmpty()) return national
        // Kept when stripping would make a valid number invalid, which is the
        // check that stops Argentina's `0` being taken off a number that needs it.
        if (general.hasPattern && general.matches(national) && !general.matches(candidate)) return national
        return candidate
    }

    // ------------------------------------------------------------------
    // Validation and typing
    // ------------------------------------------------------------------

    override fun isValid(number: PhoneNumber): Boolean {
        val region = regionRecordFor(number.callingCode, number.nationalNumber) ?: return false
        return typeIn(number.nationalNumber, region) != PhoneNumberType.UNKNOWN
    }

    override fun typeOf(number: PhoneNumber): PhoneNumberType {
        val region = regionRecordFor(number.callingCode, number.nationalNumber) ?: return PhoneNumberType.UNKNOWN
        return typeIn(number.nationalNumber, region)
    }

    /** The type [national] would have under [region], without asking which region it is. */
    private fun typeIn(national: String, region: PhoneTerritoryRecord): PhoneNumberType {
        val general = region.generalDescription
        // The whole-territory pattern first: it is one match that rejects
        // everything the typed ones would also reject.
        if (general.hasPattern && !general.matches(national)) return PhoneNumberType.UNKNOWN

        val fixed = region.descriptionOrNull(PhoneNumberType.FIXED_LINE)
        val mobile = region.descriptionOrNull(PhoneNumberType.MOBILE)
        val isFixed = fixed?.matches(national) == true
        if (isFixed) {
            // Where the two ranges are the same pattern the metadata is saying
            // they cannot be told apart, which is a different answer from
            // "fixed line".
            return if (mobile?.matches(national) == true) PhoneNumberType.FIXED_LINE_OR_MOBILE else PhoneNumberType.FIXED_LINE
        }
        if (mobile?.matches(national) == true) return PhoneNumberType.MOBILE

        for (type in TESTED_TYPES) {
            if (region.descriptionOrNull(type)?.matches(national) == true) return type
        }
        return PhoneNumberType.UNKNOWN
    }

    override fun regionOf(number: PhoneNumber): Country? {
        val region = regionRecordFor(number.callingCode, number.nationalNumber) ?: return null
        return Country.forAlpha2OrNull(region.id)
    }

    /**
     * Which territory a number belongs to, when several share a calling code.
     *
     * The main country is the fallback and the others are selected by their
     * `leadingDigits`, which is how `+1` splits between the United States and
     * twenty-four other territories. A territory with no `leadingDigits` and no
     * main-country flag is selected by matching its own general description,
     * which is what libphonenumber falls back to.
     */
    private fun regionRecordFor(callingCode: Int, nationalNumber: String): PhoneTerritoryRecord? {
        val candidates = byCallingCode[callingCode] ?: return null
        if (candidates.size == 1) return candidates[0]
        for (candidate in candidates) {
            val leading = candidate.leadingDigitsPattern()
            if (leading != null) {
                if (leading.prefixLength(nationalNumber) >= 0) return candidate
            } else if (typeIn(nationalNumber, candidate) != PhoneNumberType.UNKNOWN) {
                // The full type check rather than the general description. The
                // general description of one NANP territory accepts the numbers
                // of all of them, so testing it would hand every `+1` number to
                // the United States; only the typed descriptions tell them
                // apart.
                return candidate
            }
        }
        return candidates.firstOrNull { it.isMainCountryForCode } ?: candidates.firstOrNull()
    }

    override fun exampleNumberOrNull(region: Country, type: PhoneNumberType): PhoneNumber? {
        val record = byId[region.name] ?: return null
        val example = record.descriptionOrNull(type)?.exampleNumber?.takeIf(String::isNotEmpty) ?: return null
        return PhoneNumber(record.callingCode, example)
    }

    // ------------------------------------------------------------------
    // Formatting
    // ------------------------------------------------------------------

    override fun format(number: PhoneNumber, format: PhoneNumberFormat): String {
        if (format == PhoneNumberFormat.E164) return number.e164
        val region = regionRecordFor(number.callingCode, number.nationalNumber)
        val national = formatNational(number.nationalNumber, region, international = format != PhoneNumberFormat.NATIONAL)
        return when (format) {
            PhoneNumberFormat.NATIONAL -> national
            PhoneNumberFormat.INTERNATIONAL ->
                buildString {
                    append('+').append(number.callingCode).append(' ').append(national)
                    if (number.extension != null) {
                        append(region?.preferredExtensionPrefix ?: " ext. ").append(number.extension)
                    }
                }
            PhoneNumberFormat.RFC3966 ->
                buildString {
                    append("tel:+").append(number.callingCode).append('-').append(hyphenate(national))
                    if (number.extension != null) append(";ext=").append(number.extension)
                }
            PhoneNumberFormat.E164 -> number.e164
        }
    }

    /**
     * The national number with its territory's grouping applied.
     *
     * Falls back to the bare digits when no rule matches, which is the honest
     * answer: an unformatted number is readable, and a rule forced onto digits
     * it was not written for is not.
     */
    private fun formatNational(national: String, region: PhoneTerritoryRecord?, international: Boolean): String {
        val rules = region?.let { formatsById[it.id] }?.formats ?: return national
        val rule = rules.firstOrNull { it.appliesTo(national) } ?: return national
        val groups = rule.pattern().capture(national) ?: return national

        val template = if (international) {
            // `NA` says the format has no international form at all, so the
            // digits go out ungrouped rather than grouped the domestic way.
            when (rule.internationalFormat) {
                "NA" -> return national
                null -> rule.format
                else -> rule.internationalFormat
            }
        } else {
            val prefixRule = rule.nationalPrefixFormattingRule
            if (prefixRule != null) applyNationalPrefixRule(rule.format, prefixRule) else rule.format
        }
        return applyGroups(template, groups).trim()
    }

    private companion object {

        /**
         * The order the remaining types are tested in.
         *
         * Order decides a tie, and ties happen: a territory can describe the
         * same range as both toll-free and shared-cost. This is
         * `PhoneNumberUtil`'s order, so the tie goes the same way.
         */
        val TESTED_TYPES = listOf(
            PhoneNumberType.TOLL_FREE,
            PhoneNumberType.PREMIUM_RATE,
            PhoneNumberType.SHARED_COST,
            PhoneNumberType.VOIP,
            PhoneNumberType.PERSONAL_NUMBER,
            PhoneNumberType.PAGER,
            PhoneNumberType.UAN,
            PhoneNumberType.VOICEMAIL,
        )

        val EXTENSION_MARKERS = listOf(";ext=", "extn", "ext.", "ext", " x", "#")
    }
}

/** `$1 $2` with the captured groups substituted, `$1` being the first. */
internal fun applyGroups(template: String, groups: List<String?>): String = buildString(template.length + 8) {
    var index = 0
    while (index < template.length) {
        val ch = template[index]
        if (ch == '$' && index + 1 < template.length && template[index + 1].isDigit()) {
            val group = template[index + 1] - '1'
            append(groups.getOrNull(group).orEmpty())
            index += 2
        } else {
            append(ch)
            index++
        }
    }
}

/**
 * [format] with its national prefix rule applied.
 *
 * The rule rewrites the format's *first group token*, and its own `$1` refers to
 * that token rather than to the first capture group. Argentina is where the
 * difference shows: its mobile format is `$2 15-$3-$4` and its rule is `0$1`, so
 * the result is `0$2 15-$3-$4`. Reading the rule's `$1` as capture group one
 * would drop the prefix entirely, because there is no `$1` in the format to
 * replace.
 */
internal fun applyNationalPrefixRule(format: String, rule: String): String {
    var index = 0
    while (index + 1 < format.length) {
        if (format[index] == '$' && format[index + 1].isDigit()) {
            val token = format.substring(index, index + 2)
            return format.substring(0, index) + rule.replace("$1", token) + format.substring(index + 2)
        }
        index++
    }
    return format
}

/**
 * [formatted] with every run of punctuation reduced to a single hyphen.
 *
 * RFC 3966 allows only hyphens between digit groups, and a locale's own grouping
 * character is whatever it writes: New Caledonia separates with full stops, and
 * `tel:+687-20.12.34` is not a URI anyone should be handed.
 */
internal fun hyphenate(formatted: String): String = buildString(formatted.length) {
    var pendingSeparator = false
    for (ch in formatted) {
        if (ch.isDigit()) {
            if (pendingSeparator && isNotEmpty()) append('-')
            pendingSeparator = false
            append(ch)
        } else {
            pendingSeparator = true
        }
    }
}
