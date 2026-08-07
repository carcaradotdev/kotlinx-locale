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

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.personname.PersonName
import dev.carcara.kotlinx.locale.personname.PersonNameUsage
import dev.carcara.kotlinx.locale.personname.cldr.personNameFormat

/** Both paths a name is used through: written out, and reduced to initials. */
@JsExport
public fun probe(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    val name = PersonName(given = "Iris", surname = "Adler", locale = locale)
    return personNameFormat(name, locale = locale) +
        " " + personNameFormat(name, usage = PersonNameUsage.MONOGRAM, locale = locale)
}
