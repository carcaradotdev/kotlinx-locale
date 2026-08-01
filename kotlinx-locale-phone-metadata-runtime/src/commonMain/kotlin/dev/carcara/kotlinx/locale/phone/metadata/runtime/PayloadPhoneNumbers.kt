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
        val (body, extension) = splitExtension(dropSecondNumber(unwrapRfc3966(normalizeWideForms(text))))
        // A hash that survived extension splitting was never an extension
        // marker, and a hash is not a digit. `+1123-456-7890 7777777#` has
        // seven digits after the space where the American form allows six, so
        // the hash belongs to nothing and the input is not one number.
        if ('#' in body) return failed(PhoneParseFailure.NOT_A_NUMBER)
        val digitsAndPlus = keepDialable(body)
        if (digitsAndPlus.none(Char::isDigit)) return failed(PhoneParseFailure.NOT_A_NUMBER)

        val region = defaultRegion?.let { byId[it.name] }
        val stripped = stripInternationalPrefix(digitsAndPlus, region)

        var callingCode: Int
        var national: String
        if (stripped.hadPrefix) {
            var code = readCallingCode(stripped.digits)
            var rest = stripped.digits
            if (code == null && digitsAndPlus.startsWith('+')) {
                // A plus followed by something that is not a calling code, which
                // in practice means a plus followed by the region's own
                // international prefix: people paste `+011 64 …` in the United
                // States. libphonenumber ignores the plus and reads the IDD, and
                // its own tests say so in as many words.
                val retry = stripInternationalPrefix(stripped.digits, region)
                if (retry.hadPrefix) {
                    code = readCallingCode(retry.digits)
                    rest = retry.digits
                }
            }
            callingCode = code ?: return failed(PhoneParseFailure.UNKNOWN_CALLING_CODE)
            national = rest.substring(callingCodeLength(callingCode))
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
            else -> PhoneParseResult.Parsed(
                PhoneNumber(
                    callingCode = callingCode,
                    nationalNumber = national,
                    extension = extension,
                    // Already resolved: stripping the national prefix had to
                    // decide which territory this is before it could know which
                    // prefix to strip.
                    region = resolvedRegion(callingCode, national)?.let { Country.forAlpha2OrNull(it.id) },
                    regionCode = resolvedRegion(callingCode, national)?.id?.takeIf { it.any(Char::isLetter) },
                ),
            )
        }
    }

    private fun failed(reason: PhoneParseFailure) = PhoneParseResult.Failed(reason)

    /**
     * [text] with its wide and non-ASCII forms written the ordinary way.
     *
     * Numbers arrive typed on the input method someone has. A Japanese keyboard
     * produces fullwidth `ｅｘｔｎ` and `４５６`, and an Arabic one produces
     * `٤٥٦`; both are the same characters as far as anyone reading them is
     * concerned. Done first, so everything downstream can look for `x` and `4`
     * and find them.
     */
    private fun normalizeWideForms(text: String): String {
        // Anything outside ASCII may need rewriting, not only the fullwidth
        // block: a combining accent is 0x301 and a precomposed one is 0xF3.
        if (text.all { it.code < ASCII_LIMIT }) return text
        return buildString(text.length) {
            for (ch in text) {
                when {
                    // The fullwidth block is ASCII shifted by a fixed offset.
                    ch.code in WIDE_FORM_START..WIDE_FORM_END -> append((ch.code - WIDE_FORM_OFFSET).toChar())
                    // A combining accent is dropped and a precomposed one is
                    // written plain, so `extensión` and `extensio` plus a
                    // combining acute both read as `extension`. Spanish writes
                    // the label with an accent and Java source can spell that
                    // either way; the label is the same word.
                    ch.code in COMBINING_MARK_START..COMBINING_MARK_END -> Unit
                    ch == 'ó' -> append('o')
                    ch.isDigit() -> append(ch.digitToIntOrNull()?.digitToChar() ?: ch)
                    else -> append(ch)
                }
            }
        }
    }

    /**
     * [text] cut at the point a second number starts.
     *
     * `(212)123-1234 x508/x1234` is two numbers written together, which is a
     * thing directories do. The slash before the second extension marker is the
     * signal, and without cutting there the first number's extension runs into
     * the second number and neither is read.
     */
    private fun dropSecondNumber(text: String): String {
        var index = 0
        while (index < text.length) {
            if (text[index] == '/' || text[index] == '\\') {
                var after = index + 1
                while (after < text.length && text[after] == ' ') after++
                if (after < text.length && (text[after] == 'x' || text[after] == 'X')) return text.substring(0, index)
            }
            index++
        }
        return text
    }

    /**
     * A `tel:` URI reduced to the number inside it.
     *
     * `phone-context` is not decoration. RFC 3966 lets a URI carry a local
     * number plus the prefix that makes it global, so
     * `tel:331-6005;phone-context=+64-3` is `+64 3 331 6005` and reading only
     * the part before the semicolon loses a digit of the area code. When the
     * context is a domain name rather than a prefix it carries no digits and
     * only the local number is kept.
     *
     * `isub` is an ISDN subaddress, which addresses equipment behind the number
     * rather than the number, so it is dropped.
     */
    private fun unwrapRfc3966(text: String): String {
        val contextAt = text.indexOf(RFC3966_CONTEXT)
        val body = if (contextAt < 0) {
            text
        } else {
            val context = text.substring(contextAt + RFC3966_CONTEXT.length).substringBefore(';')
            val start = text.indexOf(RFC3966_PREFIX).let { if (it >= 0) it + RFC3966_PREFIX.length else 0 }
            val local = text.substring(start, contextAt)
            if (context.startsWith('+')) context + local else local
        }
        val isdnAt = body.indexOf(RFC3966_ISDN)
        return if (isdnAt > 0) body.substring(0, isdnAt) else body
    }

    /**
     * The input split at its extension, which is always the last thing in it.
     *
     * Modelled on libphonenumber's own pattern rather than on a list of words,
     * because the interesting part is not which labels exist but how much each
     * one is trusted. A label that says `extension` outright can be followed by
     * twenty digits; a bare `x` by nine; a hyphen and a hash, which is only an
     * extension by American convention, by six. The limits are what stop two
     * numbers written side by side from being read as one number with a very
     * long extension.
     *
     * Requiring the extension to end the input is the other half of that. It is
     * what keeps Poland's `0~0` international prefix from reading as a `~`
     * extension in `0~01-650-253-0000`.
     */
    private fun splitExtension(text: String): Pair<String, String?> {
        for (rule in EXTENSION_RULES) {
            var at = if (rule.label.isEmpty()) -1 else text.lowercase().indexOf(rule.label)
            while (at > 0) {
                val digits = readExtension(text, at + rule.label.length, rule)
                if (digits != null) return text.substring(0, at) to digits
                at = text.lowercase().indexOf(rule.label, at + 1)
            }
        }
        return trailingHashExtension(text) ?: (text to null)
    }

    /**
     * The digits of an extension starting after its label, or `null`.
     *
     * The label may be followed by a full stop or colon, then separators, then
     * the digits, then an optional hash that closes it on some systems.
     */
    private fun readExtension(text: String, from: Int, rule: ExtensionRule): String? {
        var index = from
        if (index < text.length && (text[index] == '.' || text[index] == ':')) index++
        while (index < text.length && text[index] in EXTENSION_SEPARATORS) index++
        val start = index
        while (index < text.length && text[index].isDigit()) index++
        if (index == start || index - start > rule.maxDigits) return null
        val digits = text.substring(start, index)
        if (index < text.length && text[index] == '#') index++
        while (index < text.length && text[index] == ' ') index++
        return if (index == text.length) digits else null
    }

    /**
     * The American form: a separator, up to six digits, and a required hash.
     *
     * `- 503#` is an extension and `- 503` is the end of a phone number, so the
     * hash is what distinguishes them and cannot be optional here.
     */
    private fun trailingHashExtension(text: String): Pair<String, String?>? {
        if (!text.endsWith('#')) return null
        var index = text.length - 1
        val end = index
        while (index > 0 && text[index - 1].isDigit()) index--
        if (index == end || end - index > AMERICAN_EXTENSION_DIGITS) return null
        val digitsStart = index
        while (index > 0 && (text[index - 1] == '-' || text[index - 1] == ' ')) index--
        if (index == digitsStart) return null
        return text.substring(0, index) to text.substring(digitsStart, end)
    }

    /**
     * The input reduced to digits and a leading plus, which is all that carries
     * meaning.
     *
     * Letters are dialled digits when there are enough of them to be a word.
     * `0800 DDA 005` is a real New Zealand number and `1-800-FLOWERS` is a real
     * American one, and on a keypad both are digits already; three letters is
     * libphonenumber's threshold for reading them that way rather than as
     * stray characters in a number someone pasted badly.
     */
    private fun keepDialable(text: String): String {
        // Everything before the first digit or plus is not part of the number.
        // A `tel:` scheme, a label someone pasted along with it, a currency
        // symbol. Dropped before the letters are counted, so `tel:` cannot make
        // an ordinary number look like a word and dial as 835.
        val start = text.indexOfFirst { it.isDigit() || it == '+' || it == '＋' }
        if (start < 0) return ""
        val body = text.substring(start)
        val mapLetters = body.count { it in 'a'..'z' || it in 'A'..'Z' } >= VANITY_LETTER_THRESHOLD
        return buildString(body.length) {
            for (ch in body) {
                when {
                    ch.isDigit() -> append(ch)
                    (ch == '+' || ch == '＋') && isEmpty() -> append('+')
                    mapLetters && ch.isLetter() -> keypadDigit(ch)?.let(::append)
                }
            }
        }
    }

    /** The digit [ch] shares a key with, on the ITU E.161 keypad every phone has. */
    private fun keypadDigit(ch: Char): Char? = when (ch.lowercaseChar()) {
        in 'a'..'c' -> '2'
        in 'd'..'f' -> '3'
        in 'g'..'i' -> '4'
        in 'j'..'l' -> '5'
        in 'm'..'o' -> '6'
        in 'p'..'s' -> '7'
        in 't'..'v' -> '8'
        in 'w'..'z' -> '9'
        else -> null
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
        // Or when keeping the code makes the number longer than the territory
        // has room for. `64(0)64123456` typed in New Zealand is eleven digits
        // where ten is the most any of its numbers has, so the leading 64 is a
        // country code however little the rest validates: no reading that keeps
        // it can be right.
        val keptIsTooLong = general.possibleLengths.isNotEmpty() && digits.length > general.possibleLengths.last()
        return if ((!keptIsValid && strippedIsValid) || keptIsTooLong) without else digits
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
            // The rule rewrites the part the prefix pattern matched, and the
            // rest of the number follows it unchanged. Argentina is the reason
            // this exists: dialling `0 343 15 5551212` reaches the same mobile
            // as `+54 9 343 5551212`, so the rule turns the `0…15` wrapper into
            // a leading 9, and dropping the subscriber digits along with the
            // wrapper would leave an area code and nothing to dial.
            applyGroups(transform, groups) + national.substring(end)
        } else {
            national.substring(end)
        }
        if (candidate.isEmpty()) return national
        // Kept when stripping would make a valid number invalid, which is the
        // check that stops Argentina's `0` being taken off a number that needs it.
        if (general.hasPattern && general.matches(national) && !general.matches(candidate)) return national
        // And kept when what is left is a length the territory does not have.
        // `123-456-7890` typed in the United States is not a valid number either
        // way, but taking the leading 1 off leaves nine digits where every
        // American number has ten, so the 1 was a digit rather than a prefix.
        if (!isPlausibleLengthAfterStripping(candidate, general)) return national
        return candidate
    }

    /**
     * Whether [candidate] has a length the territory could have.
     *
     * Accepts a length the territory declares, and accepts one longer than any
     * it declares: an over-long number is a number with something extra on it,
     * which is a different problem from a prefix having been taken off the
     * front. Everything else, too short or between the declared lengths, means
     * the digits removed were not a prefix.
     */
    private fun isPlausibleLengthAfterStripping(candidate: String, general: PhoneDescription): Boolean {
        val lengths = general.possibleLengths
        if (lengths.isEmpty()) return true
        return candidate.length in lengths || candidate.length > lengths.last()
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

    override fun regionCodeOf(number: PhoneNumber): String? =
        resolvedRegion(number.callingCode, number.nationalNumber)?.id?.takeIf { it.any(Char::isLetter) }

    override fun regionOf(number: PhoneNumber): Country? =
        resolvedRegion(number.callingCode, number.nationalNumber)?.let { Country.forAlpha2OrNull(it.id) }

    /**
     * The territory a number belongs to, or `null` when none claims it.
     *
     * Different from [regionRecordFor] in exactly one case, and it is the case
     * that matters here. Where several territories share a calling code and none
     * of them recognises the number, this reports nothing, because nothing is
     * true: `+1 33669` is five digits and is not a number in the United States
     * or in any of the twenty-four territories beside it. [regionRecordFor]
     * falls back to the main country instead, which is right for formatting,
     * since the alternative is refusing to write out digits somebody has, and
     * wrong for answering "where is this from".
     */
    private fun resolvedRegion(callingCode: Int, nationalNumber: String): PhoneTerritoryRecord? {
        val candidates = byCallingCode[callingCode] ?: return null
        if (candidates.size == 1) return candidates[0]
        return matchingRegion(candidates, nationalNumber)
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
        return matchingRegion(candidates, nationalNumber)
            ?: candidates.firstOrNull { it.isMainCountryForCode }
            ?: candidates.firstOrNull()
    }

    /** The first of [candidates] that recognises [nationalNumber], or `null`. */
    private fun matchingRegion(candidates: List<PhoneTerritoryRecord>, nationalNumber: String): PhoneTerritoryRecord? {
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
        return null
    }

    override fun exampleNumberOrNull(region: Country, type: PhoneNumberType): PhoneNumber? {
        val record = byId[region.name] ?: return null
        val example = record.descriptionOrNull(type)?.exampleNumber?.takeIf(String::isNotEmpty) ?: return null
        return PhoneNumber(record.callingCode, example, region = region)
    }

    /**
     * An as-you-type formatter for the territory keyed by [territoryKey].
     *
     * Takes the key rather than a [Country] so the non-geographic plans can be
     * reached too; the extension in this module's public surface takes a country.
     */
    public fun asYouTypeFor(territoryKey: String): AsYouTypeFormatter {
        val record = byId[territoryKey]
        return AsYouTypeFormatter(record, record?.let { formatsById[it.id] }?.formats.orEmpty())
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

        /**
         * The labels, longest first, with how many digits each is trusted for.
         *
         * The lengths are libphonenumber's and they are the point: an explicit
         * word is worth twenty digits, an auto-dialling separator fifteen, a
         * single ambiguous character nine.
         */
        val EXTENSION_RULES: List<ExtensionRule> = listOf(
            ExtensionRule(";ext=", 20),
            // Every form of `e?xt(?:ensi(?:ó)?)?n?`, longest first, with the
            // accent already normalised to a plain `o`. Enumerated rather than
            // matched, because a pattern here would be the one place this
            // library evaluated a regular expression it had not bounded.
            ExtensionRule("extension", 20),
            ExtensionRule("extensio", 20),
            ExtensionRule("extensin", 20),
            ExtensionRule("extensi", 20),
            ExtensionRule("xtension", 20),
            ExtensionRule("xtensio", 20),
            ExtensionRule("xtensin", 20),
            ExtensionRule("xtensi", 20),
            ExtensionRule("extn", 20),
            ExtensionRule("ext", 20),
            ExtensionRule("xtn", 20),
            ExtensionRule("xt", 20),
            // Russian and Portuguese, which libphonenumber carries alongside the
            // English ones because the label is what a local user would type.
            ExtensionRule("\u0434\u043E\u0431", 20),
            ExtensionRule("anexo", 20),
            ExtensionRule(";", 15),
            ExtensionRule(",,", 15),
            ExtensionRule("int", 9),
            ExtensionRule("x", 9),
            ExtensionRule("#", 9),
            ExtensionRule("~", 9),
            ExtensionRule(",", 9),
        )

        const val EXTENSION_SEPARATORS = " \t,-"

        /** The American hyphen-and-hash form, which is trusted least. */
        const val AMERICAN_EXTENSION_DIGITS = 6

        /** How many letters make an input a word rather than a typo. */
        const val VANITY_LETTER_THRESHOLD = 3

        const val ASCII_LIMIT = 0x80

        /** Unicode's combining diacritical marks. */
        const val COMBINING_MARK_START = 0x0300
        const val COMBINING_MARK_END = 0x036F

        /** The fullwidth ASCII block, and the constant offset back to ASCII. */
        const val WIDE_FORM_START = 0xFF01
        const val WIDE_FORM_END = 0xFF5E
        const val WIDE_FORM_OFFSET = 0xFEE0

        const val RFC3966_PREFIX = "tel:"
        const val RFC3966_CONTEXT = ";phone-context="
        const val RFC3966_ISDN = ";isub="
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

/** One extension label and how many digits it is trusted to introduce. */
internal class ExtensionRule(val label: String, val maxDigits: Int)
