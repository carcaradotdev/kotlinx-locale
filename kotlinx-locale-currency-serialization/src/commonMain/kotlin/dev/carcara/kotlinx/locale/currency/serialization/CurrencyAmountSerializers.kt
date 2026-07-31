package dev.carcara.kotlinx.locale.currency.serialization

import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.CurrencyAmount
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.currency.forCodeOrNull
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

// Every form here is locale-independent, and deliberately so. A CurrencyAmount
// has two string representations: `toDecimalString`, which writes ASCII digits
// and a `.` and nothing else, and `format(locale)`, which writes what a person
// in that locale expects to read — grouping separators, a symbol, and on some
// locales Arabic-Indic digits and a narrow no-break space. Only the first can be
// a wire format. The second cannot be read back without knowing which locale
// wrote it, and it moves between CLDR releases, so an amount stored under one
// release could come back a different number under the next. None of these
// serializers touches Locale, and this module depends on no CLDR data.

/**
 * Encodes a [CurrencyAmount] as its currency and its count of ISO minor units,
 * which is the state the class actually holds.
 *
 * ```text
 * {"currency":"USD","minorUnits":123456}   $1,234.56
 * {"currency":"JPY","minorUnits":500}      ¥500, no minor units
 * {"currency":"BHD","minorUnits":1234}     1.234 BHD, three of them
 * ```
 *
 * The scale is not in the payload: `123456` means $1,234.56 only because
 * [Currency.minorUnitDigits] says USD has two of them. That is exact and cheap
 * to read, and it is the right choice for a message in flight, where both ends
 * were built against the same ISO data. For rows that outlive a release,
 * [CurrencyAmountDecimalSerializer] puts the scale in the payload and so
 * survives a currency being redenominated underneath them.
 *
 * The `currency` field is written and read by [CurrencyCodeSerializer].
 */
public object CurrencyAmountMinorUnitsSerializer : KSerializer<CurrencyAmount> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("dev.carcara.kotlinx.locale.currency.serialization.CurrencyAmountMinorUnitsSerializer") {
            element("currency", CurrencyCodeSerializer.descriptor)
            element<Long>("minorUnits")
        }

    override fun serialize(encoder: Encoder, value: CurrencyAmount) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, CurrencyCodeSerializer, value.currency)
            encodeLongElement(descriptor, 1, value.minorUnits)
        }
    }

    override fun deserialize(decoder: Decoder): CurrencyAmount = decoder.decodeStructure(descriptor) {
        var currency: Currency? = null
        var minorUnits: Long? = null
        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                0 -> currency = decodeSerializableElement(descriptor, 0, CurrencyCodeSerializer)
                1 -> minorUnits = decodeLongElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected index: $index")
            }
        }
        CurrencyAmount(
            currency ?: throw SerializationException("Missing field 'currency' in ${descriptor.serialName}"),
            minorUnits ?: throw SerializationException("Missing field 'minorUnits' in ${descriptor.serialName}"),
        )
    }
}

/**
 * Encodes a [CurrencyAmount] as its currency and the plain ISO decimal, the one
 * [CurrencyAmount.toDecimalString] writes.
 *
 * ```text
 * {"currency":"USD","amount":"1234.56"}
 * {"currency":"JPY","amount":"500"}
 * {"currency":"BHD","amount":"1.234"}
 * {"currency":"USD","amount":"-12.50"}
 * ```
 *
 * The decimal point makes the scale part of the payload rather than something a
 * reader has to look up, so a stored amount still means what it meant if ISO
 * changes a currency's minor units, or if the reader was built against a
 * narrowed `-types`. It costs a parse on the way in, and the value is text where
 * [CurrencyAmountMinorUnitsSerializer] writes a number.
 *
 * Reading is as strict as [CurrencyAmount.parse]: an optional `-`, digits, and
 * at most [Currency.minorUnitDigits] fraction digits after `.`. `"1234.567"` is
 * a [SerializationException] for USD, rather than a silently rounded amount. The
 * `amount` string is never locale-formatted — `"1,234.56"` does not parse.
 *
 * The `currency` field is written and read by [CurrencyCodeSerializer].
 */
public object CurrencyAmountDecimalSerializer : KSerializer<CurrencyAmount> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("dev.carcara.kotlinx.locale.currency.serialization.CurrencyAmountDecimalSerializer") {
            element("currency", CurrencyCodeSerializer.descriptor)
            element<String>("amount")
        }

    override fun serialize(encoder: Encoder, value: CurrencyAmount) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, CurrencyCodeSerializer, value.currency)
            encodeStringElement(descriptor, 1, value.toDecimalString())
        }
    }

    override fun deserialize(decoder: Decoder): CurrencyAmount = decoder.decodeStructure(descriptor) {
        var currency: Currency? = null
        var amount: String? = null
        // The two fields are collected before either is used: an amount cannot
        // be parsed without knowing the currency's fraction digits, and nothing
        // says a format hands the elements over in declaration order.
        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                0 -> currency = decodeSerializableElement(descriptor, 0, CurrencyCodeSerializer)
                1 -> amount = decodeStringElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected index: $index")
            }
        }
        val resolved = currency ?: throw SerializationException("Missing field 'currency' in ${descriptor.serialName}")
        val text = amount ?: throw SerializationException("Missing field 'amount' in ${descriptor.serialName}")
        CurrencyAmount.parseOrNull(resolved, text)
            ?: throw SerializationException("Cannot parse ${resolved.code} amount: '$text'")
    }
}

/**
 * Encodes a [CurrencyAmount] as one string carrying both parts, the ISO 4217
 * code and the plain ISO decimal separated by a space: `"USD 1234.56"`.
 *
 * ```text
 * "USD 1234.56"
 * "JPY 500"
 * "BHD 1.234"
 * "USD -12.50"
 * ```
 *
 * One field instead of two, which is what makes it the form to reach for when
 * the amount has to fit somewhere that holds a single scalar: a map key, a query
 * parameter, a log line, a column you would rather not split in two. It is also
 * what [CurrencyAmount.toString] writes, so a value pasted out of a log reads
 * back in.
 *
 * The amount half is parsed by [CurrencyAmount.parse] and the code half by
 * [Currency.forCodeOrNull], so both are as strict as they are there. `"USD"`
 * alone, `"1234.56"` alone and `"$1,234.56"` are all a
 * [SerializationException].
 */
public object CurrencyAmountCodeAndDecimalSerializer : KSerializer<CurrencyAmount> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "dev.carcara.kotlinx.locale.currency.serialization.CurrencyAmountCodeAndDecimalSerializer",
            PrimitiveKind.STRING,
        )

    override fun serialize(encoder: Encoder, value: CurrencyAmount) {
        encoder.encodeString("${value.currency.code} ${value.toDecimalString()}")
    }

    override fun deserialize(decoder: Decoder): CurrencyAmount {
        val text = decoder.decodeString()
        val space = text.indexOf(' ')
        if (space < 0) throw SerializationException("Not a code and an amount: '$text'")
        val code = text.substring(0, space)
        val currency = Currency.forCodeOrNull(code) ?: throw SerializationException("Unknown ISO 4217 code: '$code'")
        val amount = text.substring(space + 1)
        return CurrencyAmount.parseOrNull(currency, amount)
            ?: throw SerializationException("Cannot parse ${currency.code} amount: '$amount'")
    }
}
