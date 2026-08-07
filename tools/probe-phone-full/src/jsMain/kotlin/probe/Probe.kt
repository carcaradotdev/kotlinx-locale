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

@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.phone.PhoneNumberFormat
import dev.carcara.kotlinx.locale.phone.metadata.format
import dev.carcara.kotlinx.locale.phone.metadata.isValid
import dev.carcara.kotlinx.locale.phone.metadata.phoneNumberOrNull

/** Parse, validate and format: the three calls a signup form makes. */
@JsExport
public fun probe(text: String): String {
    val number = phoneNumberOrNull(text, Country.GB) ?: return "unparsed"
    return listOf(
        number.isValid().toString(),
        number.region?.name.orEmpty(),
        number.format(PhoneNumberFormat.NATIONAL),
        number.format(PhoneNumberFormat.E164),
    ).joinToString(" ")
}
