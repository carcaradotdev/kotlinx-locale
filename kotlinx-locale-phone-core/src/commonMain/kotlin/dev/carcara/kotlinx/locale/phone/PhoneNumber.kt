package dev.carcara.kotlinx.locale.phone

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.country.Country

/**
 * A phone number, as its calling code and its national number.
 *
 * The national number is held as digits rather than as a `Long`, which is the
 * one place this deliberately differs from libphonenumber's own model. That
 * model carries a `long` alongside an `italianLeadingZero` flag and a
 * `numberOfLeadingZeros` count, because a `long` cannot hold `0212345678` and
 * Italy needs it to. Digits hold it by construction, and the same reasoning
 * applies here as to [dev.carcara.kotlinx.locale.number.Decimal]: what is being
 * modelled is what someone dials, and that is a string of digits with a length.
 *
 * Construct one by parsing. The constructor is marked internal to the library
 * rather than hidden, because the metadata modules have to build one and they
 * are not in this module; a number assembled from parts by anyone else has
 * skipped the only step that decides whether those parts go together.
 */
public class PhoneNumber @InternalKotlinxLocaleApi public constructor(
    /** The country calling code, without the `+`: 44 for the United Kingdom. */
    public val callingCode: Int,
    /** The national significant number, digits only, leading zeros intact. */
    public val nationalNumber: String,
    /** The extension, when the input carried one. */
    public val extension: String? = null,
) {

    /**
     * The E.164 form: `+`, the calling code, the national number.
     *
     * The one representation that identifies a number without also saying where
     * it is being dialled from, which is what makes it the form to store.
     * Extensions are not part of E.164 and are not included.
     */
    public val e164: String get() = "+$callingCode$nationalNumber"

    /** The number of digits in the national number, which is what validation is over. */
    public val nationalNumberLength: Int get() = nationalNumber.length

    override fun equals(other: Any?): Boolean = other is PhoneNumber &&
        callingCode == other.callingCode &&
        nationalNumber == other.nationalNumber &&
        extension == other.extension

    override fun hashCode(): Int {
        var result = callingCode
        result = 31 * result + nationalNumber.hashCode()
        result = 31 * result + (extension?.hashCode() ?: 0)
        return result
    }

    /** The E.164 form, with the extension appended when there is one. */
    override fun toString(): String = if (extension == null) e164 else "$e164;ext=$extension"
}

/**
 * What kind of number this is, in libphonenumber's vocabulary.
 *
 * The distinctions are the ones telcos bill on rather than the ones a user would
 * draw, which is why [PREMIUM_RATE] and [SHARED_COST] are separate and why
 * [FIXED_LINE_OR_MOBILE] exists at all: in North America the two ranges are not
 * distinguishable, and reporting either one would be a guess.
 */
public enum class PhoneNumberType {
    FIXED_LINE,
    MOBILE,

    /** The ranges overlap in this territory, so the number is one or the other. */
    FIXED_LINE_OR_MOBILE,
    TOLL_FREE,
    PREMIUM_RATE,
    SHARED_COST,
    VOIP,
    PERSONAL_NUMBER,
    PAGER,

    /** A universal access number, which is routed rather than geographic. */
    UAN,
    VOICEMAIL,

    /** No description in the territory's metadata matches. */
    UNKNOWN,
}

/** The written forms of a number. */
public enum class PhoneNumberFormat {

    /** `+441212345678`. Storage and interchange. */
    E164,

    /** `+44 121 234 5678`. Dialling from abroad, or showing provenance. */
    INTERNATIONAL,

    /** `0121 234 5678`. Dialling from inside the same territory. */
    NATIONAL,

    /** `tel:+44-121-234-5678`. The RFC 3966 URI, extension included. */
    RFC3966,
}

/**
 * Why a number could not be parsed.
 *
 * Separate values rather than one failure, because the three cases call for
 * different things at a call site: text with no number in it is a user still
 * typing, a number that is too short is a user part way through, and an unknown
 * calling code is a number that will never be valid however much more arrives.
 */
public enum class PhoneParseFailure {

    /** Nothing in the input looked like a phone number. */
    NOT_A_NUMBER,

    /** No calling code, and no default region to supply one. */
    MISSING_REGION,

    /** A calling code no territory claims. */
    UNKNOWN_CALLING_CODE,

    /** Fewer digits than any territory's shortest number. */
    TOO_SHORT,

    /** More digits than E.164 permits. */
    TOO_LONG,
}

/**
 * The result of parsing: a number, or why there is not one.
 *
 * A sealed result rather than a nullable return, because [PhoneParseFailure]
 * carries the thing a caller acts on and a `null` would throw it away. The
 * `OrNull` accessor is there for the callers that genuinely do not care.
 */
public sealed class PhoneParseResult {

    public class Parsed @InternalKotlinxLocaleApi public constructor(public val number: PhoneNumber) : PhoneParseResult()

    public class Failed @InternalKotlinxLocaleApi public constructor(public val reason: PhoneParseFailure) : PhoneParseResult()

    /** The number, or `null` when parsing failed. */
    public val numberOrNull: PhoneNumber? get() = (this as? Parsed)?.number
}

/**
 * How a number's calling code got there, which decides how to format it back.
 *
 * A number the user typed with a `+` should be shown with one; a number typed
 * bare in its own country should not suddenly acquire a country code. This is
 * libphonenumber's `CountryCodeSource` under a name that says what it decides.
 */
public enum class CallingCodeSource {

    /** The input began with `+`. */
    FROM_PLUS_SIGN,

    /** The input began with the territory's international dialling prefix. */
    FROM_INTERNATIONAL_PREFIX,

    /** The input began with the calling code and no prefix at all. */
    FROM_BARE_CALLING_CODE,

    /** The input had no calling code and the default region supplied it. */
    FROM_DEFAULT_REGION,
}

/**
 * A source of phone number metadata.
 *
 * Not a `LocaleDataSource`: none of this varies by language. A number is valid
 * or not, and formats the way its territory formats, whoever is reading. The
 * pieces that do vary by language are the geocoding and carrier names, and they
 * are their own artifacts with their own contracts.
 */
public interface PhoneNumberSource {

    /** The territories this source carries metadata for. */
    public val supportedRegions: Set<Country>

    /**
     * [text] read as a number, using [defaultRegion] when it carries no calling
     * code of its own.
     *
     * Accepts what people actually type: spaces, dashes, brackets, a leading
     * `+`, an international dialling prefix, and an extension after `ext`, `x`
     * or `#`.
     */
    public fun parse(text: String, defaultRegion: Country? = null): PhoneParseResult

    /** True when [number] is a number that could exist in its territory. */
    public fun isValid(number: PhoneNumber): Boolean

    /** What kind of number [number] is, or [PhoneNumberType.UNKNOWN]. */
    public fun typeOf(number: PhoneNumber): PhoneNumberType

    /** [number] written in [format]. */
    public fun format(number: PhoneNumber, format: PhoneNumberFormat = PhoneNumberFormat.INTERNATIONAL): String

    /**
     * The territory [number] belongs to, or `null` when its calling code is not
     * geographic.
     *
     * `+800` numbers are international freephone and belong to no country, which
     * is a real answer rather than a gap.
     */
    public fun regionOf(number: PhoneNumber): Country?

    /** A valid example number of [type] for [region], for tests and placeholders. */
    public fun exampleNumberOrNull(region: Country, type: PhoneNumberType = PhoneNumberType.FIXED_LINE): PhoneNumber?

    /** The calling code [region] uses, or `null` when this source has no metadata for it. */
    public fun callingCodeOrNull(region: Country): Int?
}

/** [text] parsed, or `null`. The accessor for callers that do not act on the reason. */
public fun PhoneNumberSource.parseOrNull(text: String, defaultRegion: Country? = null): PhoneNumber? =
    parse(text, defaultRegion).numberOrNull

/** True when [text] parses to a valid number for [defaultRegion]. */
public fun PhoneNumberSource.isValid(text: String, defaultRegion: Country? = null): Boolean =
    parseOrNull(text, defaultRegion)?.let(::isValid) == true
