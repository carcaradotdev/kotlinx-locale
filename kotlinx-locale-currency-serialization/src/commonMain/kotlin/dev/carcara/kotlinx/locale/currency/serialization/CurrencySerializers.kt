/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.carcara.kotlinx.locale.currency.serialization

import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.currency.forCodeOrNull
import dev.carcara.kotlinx.locale.currency.forNumericCodeOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Encodes a [Currency] as its ISO 4217 alphabetic code, e.g. `"USD"`.
 *
 * This is also what the serialization plugin produces on its own for a
 * `Currency` property, since the enum entry names are the alphabetic codes.
 * Naming this serializer changes nothing about the output; it states the
 * contract, and it makes `Currency` usable as a root object on Kotlin/JS and
 * Kotlin/Native, where an enum the plugin never saw declared has no serializer
 * of its own.
 *
 * Reading is case-insensitive, because [Currency.forCodeOrNull] is. A code
 * outside the active list is a [SerializationException].
 */
public object CurrencyCodeSerializer : KSerializer<Currency> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "dev.carcara.kotlinx.locale.currency.serialization.CurrencyCodeSerializer",
            PrimitiveKind.STRING,
        )

    override fun serialize(encoder: Encoder, value: Currency) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): Currency {
        val code = decoder.decodeString()
        return Currency.forCodeOrNull(code) ?: throw SerializationException("Unknown ISO 4217 code: '$code'")
    }
}

/**
 * Encodes a [Currency] as its ISO 4217 numeric code, e.g. `840` for `USD`.
 *
 * A numeric code identifies a currency only among the ones still in use. ISO
 * reuses a number across generations of the same currency — 191 is both the 1991
 * Croatian dinar and the kuna that replaced it, 8 is both Albanian leks — so
 * reading a number back resolves to the active entry, and a withdrawn currency
 * does not round trip through this form. Three withdrawn codes have no number at
 * all.
 *
 * Writing an unnumbered currency throws rather than emitting a `-1` that could
 * never be read back. [CurrencyCodeSerializer] has neither edge and is the safer
 * choice for a field that must hold any currency, including a historical one.
 */
public object CurrencyNumericCodeSerializer : KSerializer<Currency> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "dev.carcara.kotlinx.locale.currency.serialization.CurrencyNumericCodeSerializer",
            PrimitiveKind.INT,
        )

    override fun serialize(encoder: Encoder, value: Currency) {
        if (value.numericCode < 0) throw SerializationException("ISO 4217 assigns no numeric code to '${value.code}'")
        encoder.encodeInt(value.numericCode)
    }

    override fun deserialize(decoder: Decoder): Currency {
        val code = decoder.decodeInt()
        return Currency.forNumericCodeOrNull(code) ?: throw SerializationException("Unknown ISO 4217 numeric code: $code")
    }
}

/**
 * Reads either ISO 4217 code from one string field and writes the alphabetic
 * one.
 *
 * The two code spaces do not overlap: the alphabetic code is three letters and
 * the numeric code is digits, so a string belongs to exactly one of them.
 * `"USD"`, `"840"` and `"978"` all read back as the currency they name, in any
 * case, and all of them are written as the alphabetic code. Use it for an input
 * you do not control.
 *
 * It reads the numeric code from a *string*, `"840"`. A JSON number `840` is a
 * different token, and a `Decoder` cannot be asked which one is coming: it has
 * to commit to `decodeString` or `decodeInt` before it looks. This serializer
 * commits to `decodeString`, so a bare JSON number fails unless the format is
 * told to be forgiving:
 *
 * ```kotlin
 * Json { isLenient = true }.decodeFromString(CurrencyLenientCodeSerializer, "840")
 * ```
 *
 * In lenient mode Json hands an unquoted token to `decodeString` as its text,
 * and `"840"` arrives here as it would have quoted. If the field is genuinely a
 * number and you would rather not loosen the whole format, name
 * [CurrencyNumericCodeSerializer] instead. The declaration then says what the
 * field holds, and read time has nothing left to resolve.
 */
public object CurrencyLenientCodeSerializer : KSerializer<Currency> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "dev.carcara.kotlinx.locale.currency.serialization.CurrencyLenientCodeSerializer",
            PrimitiveKind.STRING,
        )

    override fun serialize(encoder: Encoder, value: Currency) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): Currency {
        val code = decoder.decodeString()
        return currencyForAnyCodeOrNull(code) ?: throw SerializationException("Not an ISO 4217 code: '$code'")
    }
}

/** Resolves a code against whichever of the two spaces its shape puts it in. */
private fun currencyForAnyCodeOrNull(code: String): Currency? = when {
    code.isEmpty() -> null
    code.all { it in '0'..'9' } -> code.toIntOrNull()?.let { Currency.forNumericCodeOrNull(it) }
    else -> Currency.forCodeOrNull(code)
}
