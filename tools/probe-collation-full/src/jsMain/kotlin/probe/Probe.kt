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
import dev.carcara.kotlinx.locale.collation.CollationStrength
import dev.carcara.kotlinx.locale.collation.cldr.collationComparator

/** Both strengths a consumer reaches for: ordering a list, and matching in a search. */
@JsExport
public fun probe(tag: String, words: Array<String>): String {
    val locale = Locale.forLanguageTag(tag)
    val sorted = words.sortedWith(collationComparator(locale))
    val loose = collationComparator(locale, CollationStrength.PRIMARY).compare(words.first(), words.last())
    return sorted.joinToString(" ") + " " + loose
}
