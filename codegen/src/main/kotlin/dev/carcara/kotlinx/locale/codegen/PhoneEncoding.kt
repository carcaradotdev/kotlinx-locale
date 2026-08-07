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

package dev.carcara.kotlinx.locale.codegen

/**
 * The phone metadata as the two flat tables the runtime reads.
 *
 * Split in two because the readers are different: validation reads the
 * descriptions and never the formats, and a build that only checks numbers
 * should not carry 913 format patterns. Both are one string rather than a map,
 * because they are locale-independent and so ship once rather than per locale.
 */

/**
 * The key a territory is stored under.
 *
 * Its ISO 3166-1 code, except for the nine non-geographic entries that all carry
 * the id `001`: international freephone, the satellite services and the rest.
 * They are distinct numbering plans sharing one placeholder id, so keying them
 * by it would keep one of the nine. libphonenumber looks them up by calling
 * code, and so does this. A calling code is digits and an alpha-2 code is
 * letters, so the two cannot collide, and a lookup of `800` as a country still
 * finds nothing, which is the right answer for a number that belongs to no
 * country.
 */
private fun PhoneTerritory.key(): String = if (id == "001") countryCode.toString() else id

/** One entry per territory: the scalars, then one field per description. */
fun encodePhoneTerritories(metadata: PhoneMetadata): String = metadata.territories.joinToString(LIST_SEPARATOR) { territory ->
    val fields = ArrayList<String>(16)
    fields += territory.key()
    fields += territory.countryCode.toString()
    // Two booleans in one field, since a record with a field per flag costs
    // more than the flags do.
    fields += buildString {
        if (territory.mainCountryForCode) append('m')
        if (territory.mobileNumberPortableRegion) append('p')
    }
    fields += territory.leadingDigits.orEmpty()
    fields += territory.internationalPrefix.orEmpty()
    fields += territory.preferredInternationalPrefix.orEmpty()
    fields += territory.nationalPrefix.orEmpty()
    fields += territory.nationalPrefixForParsing.orEmpty()
    fields += territory.nationalPrefixTransformRule.orEmpty()
    fields += territory.preferredExtnPrefix.orEmpty()
    fields += territory.generalDesc.nationalNumberPattern.orEmpty()
    fields += territory.generalDesc.possibleLengths.joinToString(",")
    for ((type, desc) in territory.descriptions) {
        fields += listOf(
            type.name,
            desc.nationalNumberPattern.orEmpty(),
            desc.possibleLengths.joinToString(","),
            desc.localOnlyLengths.joinToString(","),
            desc.exampleNumber.orEmpty(),
        ).joinToString(KEY_SEPARATOR)
    }
    fields.joinToString(FIELD_SEPARATOR)
}

/** One entry per territory that has formats: the id, then one field per format. */
fun encodePhoneFormats(metadata: PhoneMetadata): String =
    metadata.territories.filter { it.formats.isNotEmpty() }.joinToString(LIST_SEPARATOR) { territory ->
        val fields = ArrayList<String>(territory.formats.size + 1)
        fields += territory.key()
        for (format in territory.formats) {
            fields += listOf(
                format.pattern,
                format.format,
                // Space-joined: these are patterns and the parser strips every
                // space out of them, so a space cannot occur inside one.
                format.leadingDigits.joinToString(" "),
                format.nationalPrefixFormattingRule.orEmpty(),
                if (format.nationalPrefixOptionalWhenFormatting) "1" else "",
                format.internationalFormat.orEmpty(),
                format.domesticCarrierCodeFormattingRule.orEmpty(),
            ).joinToString(KEY_SEPARATOR)
        }
        fields.joinToString(FIELD_SEPARATOR)
    }
