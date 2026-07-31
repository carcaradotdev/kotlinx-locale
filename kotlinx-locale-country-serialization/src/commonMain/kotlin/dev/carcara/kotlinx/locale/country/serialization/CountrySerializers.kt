package dev.carcara.kotlinx.locale.country.serialization

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.country.forAlpha2OrNull
import dev.carcara.kotlinx.locale.country.forAlpha3OrNull
import dev.carcara.kotlinx.locale.country.forNumericCodeOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Encodes a [Country] as its ISO 3166-1 alpha-2 code, e.g. `"US"`.
 *
 * This is also what the serialization plugin produces on its own for a `Country`
 * property, since the enum entry names are the alpha-2 codes. Naming this
 * serializer changes nothing about the output; it states the contract, and it
 * makes `Country` usable as a root object on Kotlin/JS and Kotlin/Native, where
 * an enum the plugin never saw declared has no serializer of its own.
 *
 * Reading is case-insensitive, because [Country.forAlpha2OrNull] is. An
 * unassigned code is a [SerializationException].
 */
public object CountryAlpha2Serializer : KSerializer<Country> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "dev.carcara.kotlinx.locale.country.serialization.CountryAlpha2Serializer",
            PrimitiveKind.STRING,
        )

    override fun serialize(encoder: Encoder, value: Country) {
        encoder.encodeString(value.alpha2)
    }

    override fun deserialize(decoder: Decoder): Country {
        val code = decoder.decodeString()
        return Country.forAlpha2OrNull(code) ?: throw SerializationException("Unknown ISO 3166-1 alpha-2 code: '$code'")
    }
}

/**
 * Encodes a [Country] as its ISO 3166-1 alpha-3 code, e.g. `"USA"`.
 *
 * Reading is case-insensitive and rejects alpha-2: `"US"` fails here, which is
 * the point of naming this serializer rather than
 * [CountryLenientCodeSerializer]. An unassigned code is a
 * [SerializationException].
 */
public object CountryAlpha3Serializer : KSerializer<Country> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "dev.carcara.kotlinx.locale.country.serialization.CountryAlpha3Serializer",
            PrimitiveKind.STRING,
        )

    override fun serialize(encoder: Encoder, value: Country) {
        encoder.encodeString(value.alpha3)
    }

    override fun deserialize(decoder: Decoder): Country {
        val code = decoder.decodeString()
        return Country.forAlpha3OrNull(code) ?: throw SerializationException("Unknown ISO 3166-1 alpha-3 code: '$code'")
    }
}

/**
 * Encodes a [Country] as its ISO 3166-1 numeric code, e.g. `840` for `US`.
 *
 * The value is a number, not the zero-padded `"840"` the standard prints, so a
 * format that distinguishes the two writes an integer. Use
 * [CountryLenientCodeSerializer] to read the padded string form.
 */
public object CountryNumericCodeSerializer : KSerializer<Country> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "dev.carcara.kotlinx.locale.country.serialization.CountryNumericCodeSerializer",
            PrimitiveKind.INT,
        )

    override fun serialize(encoder: Encoder, value: Country) {
        encoder.encodeInt(value.numericCode)
    }

    override fun deserialize(decoder: Decoder): Country {
        val code = decoder.decodeInt()
        return Country.forNumericCodeOrNull(code) ?: throw SerializationException("Unknown ISO 3166-1 numeric code: $code")
    }
}

/**
 * Reads any of the three ISO 3166-1 codes from one string field and writes
 * alpha-2.
 *
 * The three code spaces do not overlap, which is what makes this possible at
 * all: alpha-2 is two letters, alpha-3 is three, and numeric is digits, so a
 * string belongs to exactly one of them. `"US"`, `"USA"`, `"840"` and `"004"`
 * all read back as the country they name, in any case, and all of them are
 * written as `"US"`. Use it for an input you do not control — a field whose
 * producer switched from alpha-2 to alpha-3 two releases ago, and whose old rows
 * are still in the database.
 *
 * It reads the numeric code from a *string*, `"840"`. A JSON number `840` is a
 * different token, and a `Decoder` cannot be asked which one is coming: it has
 * to commit to `decodeString` or `decodeInt` before it looks. This serializer
 * commits to `decodeString`, so a bare JSON number fails unless the format is
 * told to be forgiving:
 *
 * ```kotlin
 * Json { isLenient = true }.decodeFromString(CountryLenientCodeSerializer, "840")
 * ```
 *
 * In lenient mode Json hands an unquoted token to `decodeString` as its text,
 * and `"840"` arrives here as it would have quoted. If the field is genuinely a
 * number and you would rather not loosen the whole format, name
 * [CountryNumericCodeSerializer] instead. The declaration then says what the
 * field holds, and read time has nothing left to resolve.
 */
public object CountryLenientCodeSerializer : KSerializer<Country> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "dev.carcara.kotlinx.locale.country.serialization.CountryLenientCodeSerializer",
            PrimitiveKind.STRING,
        )

    override fun serialize(encoder: Encoder, value: Country) {
        encoder.encodeString(value.alpha2)
    }

    override fun deserialize(decoder: Decoder): Country {
        val code = decoder.decodeString()
        return countryForAnyCodeOrNull(code) ?: throw SerializationException("Not an ISO 3166-1 code: '$code'")
    }
}

/**
 * Resolves a code against whichever of the three spaces its shape puts it in.
 * Digits are checked first so `"004"` reaches the numeric lookup rather than
 * being read as a three-character alpha-3.
 */
private fun countryForAnyCodeOrNull(code: String): Country? = when {
    code.isEmpty() -> null
    code.all { it in '0'..'9' } -> code.toIntOrNull()?.let { Country.forNumericCodeOrNull(it) }
    code.length == 2 -> Country.forAlpha2OrNull(code)
    code.length == 3 -> Country.forAlpha3OrNull(code)
    else -> null
}
