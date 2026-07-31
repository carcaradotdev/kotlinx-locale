package dev.carcara.kotlinx.locale.serialization

import dev.carcara.kotlinx.locale.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Encodes a [Locale] as its canonical BCP 47 language tag, e.g. `"pt-BR"`.
 *
 * Writing is exact: the tag is [Locale.toLanguageTag], so subtag case is
 * normalized whatever the instance was built from. Reading goes through
 * [Locale.forLanguageTagOrNull] and is therefore as lenient as that function,
 * which accepts POSIX identifiers such as `pt_BR.UTF-8@latin` and ignores
 * Unicode extensions. A tag with no valid language subtag is a
 * [SerializationException].
 *
 * ```kotlin
 * @Serializable
 * class Preferences(
 *     @Serializable(with = LocaleTagSerializer::class) val locale: Locale,
 * )
 * // {"locale":"pt-BR"}
 * ```
 */
public object LocaleTagSerializer : KSerializer<Locale> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.carcara.kotlinx.locale.serialization.LocaleTagSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Locale) {
        encoder.encodeString(value.toLanguageTag())
    }

    override fun deserialize(decoder: Decoder): Locale {
        val tag = decoder.decodeString()
        return Locale.forLanguageTagOrNull(tag) ?: throw SerializationException("Cannot parse language tag: '$tag'")
    }
}
