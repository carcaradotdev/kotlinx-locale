@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.phone.PhoneNumberFormat
import dev.carcara.kotlinx.locale.phone.metadata.format
import dev.carcara.kotlinx.locale.phone.metadata.isValid
import dev.carcara.kotlinx.locale.phone.metadata.toPhoneNumberOrNull

/** Parse, validate and format: the three calls a signup form makes. */
@JsExport
public fun probe(text: String): String {
    val number = text.toPhoneNumberOrNull(Country.GB) ?: return "unparsed"
    return listOf(
        number.isValid().toString(),
        number.format(PhoneNumberFormat.NATIONAL),
        number.format(PhoneNumberFormat.E164),
    ).joinToString(" ")
}
