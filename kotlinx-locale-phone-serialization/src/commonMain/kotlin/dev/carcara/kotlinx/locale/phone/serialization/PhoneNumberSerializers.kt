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

@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.phone.serialization

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.phone.PhoneNumber
import dev.carcara.kotlinx.locale.phone.PhoneNumberFormat
import dev.carcara.kotlinx.locale.phone.PhoneNumberSource
import dev.carcara.kotlinx.locale.phone.PhoneParseResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

// There is no default serializer for PhoneNumber, on purpose and for the same
// reason the currency module has none: the four written forms are not four
// spellings of one thing, they are four different amounts of information.
//
// E.164 identifies a number anywhere. The national form does not: `020 7123
// 4567` is a London number only if you already know it is British, and reading
// it back needs a region the payload does not carry. The international and RFC
// 3966 forms do identify the number, but they carry the grouping libphonenumber
// chose for that territory in the release that wrote them, and that grouping
// moves: a row written under one release can come back spaced differently under
// the next, so a column of them stops comparing equal to itself.
//
// So the names say which form, and the KDoc on each says what it costs. Store
// E.164 or store the parts; the other three are for talking to something that
// insists on them.

/**
 * Encodes a number as E.164: `"+442071234567"`.
 *
 * The form to store. It is the only one that identifies a number without also
 * saying where it is being dialled from, it is stable across libphonenumber
 * releases because it is digits rather than presentation, and it compares and
 * indexes as a string.
 *
 * Reading it back needs metadata, because splitting `+442071234567` into a
 * calling code and a national number means knowing that 44 is a calling code
 * and 4420 is not. That is why this takes a [PhoneNumberSource] rather than
 * being an object.
 *
 * ```kotlin
 * @Serializable
 * class Contact(
 *     @Serializable(with = PhoneNumberE164Serializer::class) val phone: PhoneNumber,
 * )
 * ```
 *
 * A property-level annotation cannot pass a source, so declare a subclass or an
 * object for the source your build uses:
 *
 * ```kotlin
 * object AppPhoneSerializer : PhoneNumberE164Serializer(PhoneNumbers)
 * ```
 */
public open class PhoneNumberE164Serializer(private val source: PhoneNumberSource) : KSerializer<PhoneNumber> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.carcara.kotlinx.locale.phone.serialization.E164", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PhoneNumber) {
        encoder.encodeString(value.e164)
    }

    override fun deserialize(decoder: Decoder): PhoneNumber = source.decode(decoder.decodeString(), null, "E.164")

    public companion object
}

/**
 * Encodes a number as its international form: `"+44 20 7123 4567"`.
 *
 * Readable by a person and readable back by this library, but it carries the
 * grouping of whichever libphonenumber release wrote it. Two rows written a year
 * apart can differ by a space while naming the same telephone, so this is a
 * display format that happens to round trip rather than a storage format. Prefer
 * [PhoneNumberE164Serializer] for anything you will later compare.
 */
public open class PhoneNumberInternationalSerializer(private val source: PhoneNumberSource) : KSerializer<PhoneNumber> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.carcara.kotlinx.locale.phone.serialization.International", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PhoneNumber) {
        encoder.encodeString(source.format(value, PhoneNumberFormat.INTERNATIONAL))
    }

    override fun deserialize(decoder: Decoder): PhoneNumber = source.decode(decoder.decodeString(), null, "international")

    public companion object
}

/**
 * Encodes a number as its national form: `"020 7123 4567"`.
 *
 * The form with the least information in it, and the only one that cannot be
 * read back on its own, which is why this one alone takes a [defaultRegion].
 * Two different telephones in two countries can have the same national form,
 * so a payload written for one region and read under another decodes to the
 * wrong number rather than failing.
 *
 * Use it when something on the other side insists on national numbers. Do not
 * use it to store a number whose country you are not also storing.
 */
public open class PhoneNumberNationalSerializer(
    private val source: PhoneNumberSource,
    /** The country the national form is read against. Its absence is the risk above. */
    private val defaultRegion: Country,
) : KSerializer<PhoneNumber> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.carcara.kotlinx.locale.phone.serialization.National", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PhoneNumber) {
        encoder.encodeString(source.format(value, PhoneNumberFormat.NATIONAL))
    }

    override fun deserialize(decoder: Decoder): PhoneNumber = source.decode(decoder.decodeString(), defaultRegion, "national")

    public companion object
}

/**
 * Encodes a number as an RFC 3966 URI: `"tel:+44-20-7123-4567"`.
 *
 * The form to hand to something expecting a URI, and the only one that carries
 * an extension in a way the specification defines, as `;ext=`. Same caveat as
 * the international form: the hyphens are this release's grouping.
 */
public open class PhoneNumberRfc3966Serializer(private val source: PhoneNumberSource) : KSerializer<PhoneNumber> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.carcara.kotlinx.locale.phone.serialization.Rfc3966", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PhoneNumber) {
        encoder.encodeString(source.format(value, PhoneNumberFormat.RFC3966))
    }

    override fun deserialize(decoder: Decoder): PhoneNumber = source.decode(decoder.decodeString(), null, "RFC 3966")

    public companion object
}

/**
 * Reads a number written any of the four ways, and writes E.164.
 *
 * For the boundary where you do not control the producer: a column filled by
 * three services over five years, a form field someone pasted into, an import
 * from a system that writes national numbers on Tuesdays. The parser this
 * delegates to already accepts punctuation, dialling prefixes and extensions,
 * so accepting all four forms costs nothing extra.
 *
 * Asymmetric on purpose, in the same way the currency module's lenient code
 * serializer is: reading is where the mess is and writing is where it stops.
 * Everything this writes is E.164, so a column read leniently once is a column
 * written strictly thereafter.
 *
 * [defaultRegion] is what a national form is read against, and `null` means a
 * national form is rejected rather than guessed at.
 */
public open class LenientPhoneNumberSerializer(private val source: PhoneNumberSource, private val defaultRegion: Country? = null) :
    KSerializer<PhoneNumber> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.carcara.kotlinx.locale.phone.serialization.Lenient", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PhoneNumber) {
        encoder.encodeString(value.e164)
    }

    override fun deserialize(decoder: Decoder): PhoneNumber = source.decode(decoder.decodeString(), defaultRegion, "phone number")

    public companion object
}

/**
 * Encodes a number as the parts it is made of.
 *
 * ```text
 * {"callingCode":44,"nationalNumber":"2071234567"}
 * {"callingCode":39,"nationalNumber":"0212345678"}   the leading zero Italy needs
 * ```
 *
 * The only serializer here that needs no metadata, because it neither formats
 * nor parses: what goes on the wire is what the class holds. That makes it the
 * one to reach for in a build that took `-phone-core` and no tables, and the one
 * that cannot be wrong about a number whose territory a later libphonenumber
 * release reassigns.
 *
 * The national number is a string rather than a number, and that is the point:
 * `0212345678` is a Rome landline and `212345678` is not the same telephone.
 */
public object PhoneNumberPartsSerializer : KSerializer<PhoneNumber> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("dev.carcara.kotlinx.locale.phone.serialization.Parts") {
            element<Int>("callingCode")
            element<String>("nationalNumber")
            element<String?>("extension", isOptional = true)
        }

    override fun serialize(encoder: Encoder, value: PhoneNumber) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.callingCode)
            encodeStringElement(descriptor, 1, value.nationalNumber)
            value.extension?.let { encodeStringElement(descriptor, 2, it) }
        }
    }

    override fun deserialize(decoder: Decoder): PhoneNumber = decoder.decodeStructure(descriptor) {
        var callingCode: Int? = null
        var nationalNumber: String? = null
        var extension: String? = null
        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                0 -> callingCode = decodeIntElement(descriptor, 0)
                1 -> nationalNumber = decodeStringElement(descriptor, 1)
                2 -> extension = decodeStringElement(descriptor, 2)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected index $index in a phone number")
            }
        }
        val code = callingCode ?: throw SerializationException("A phone number needs a callingCode")
        val digits = nationalNumber ?: throw SerializationException("A phone number needs a nationalNumber")
        if (digits.isEmpty() || digits.any { !it.isDigit() }) {
            throw SerializationException("'$digits' is not a national number: it must be digits")
        }
        // No region: this serializer carries no metadata and so cannot resolve
        // one. Ask the source for it if you need it.
        PhoneNumber(code, digits, extension)
    }
}

/** [text] parsed, or a [SerializationException] naming the form that was expected. */
private fun PhoneNumberSource.decode(text: String, region: Country?, form: String): PhoneNumber = when (val result = parse(text, region)) {
    is PhoneParseResult.Parsed -> result.number
    is PhoneParseResult.Failed ->
        throw SerializationException("'$text' is not a $form phone number: ${result.reason}")
}
