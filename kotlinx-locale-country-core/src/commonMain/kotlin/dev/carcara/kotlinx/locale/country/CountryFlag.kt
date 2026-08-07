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

package dev.carcara.kotlinx.locale.country

private const val REGIONAL_INDICATOR_A = 0x1F1E6

/**
 * The flag emoji for this country: the two regional indicator symbols that spell
 * its alpha-2 code, so `BR` is U+1F1E7 U+1F1F7.
 *
 * Derived rather than looked up, because the derivation is the definition. UTS
 * #51 builds a flag from the code and nothing else, so there is no table to
 * carry and nothing for a narrowed build to drop.
 *
 * What is not automatic is that the result is a sequence Unicode recommends for
 * general interchange, and that is checked at generation time against the RGI
 * flag sequences of the pinned Emoji release. Every entry of [Country] is on
 * that list, so this is total and has no nullable form. A CLDR release adding a
 * region Unicode has no flag for would fail generation rather than ship a
 * sequence nothing renders.
 *
 * RGI is a recommendation to vendors, not a promise about any particular device.
 * Windows draws these as the two letters rather than as a flag.
 */
public val Country.flagEmoji: String
    get() = buildString(4) {
        appendRegionalIndicator(name[0])
        appendRegionalIndicator(name[1])
    }

/** The astral code point as its UTF-16 surrogate pair, which is what every Kotlin target stores. */
private fun StringBuilder.appendRegionalIndicator(letter: Char) {
    val codePoint = REGIONAL_INDICATOR_A + (letter - 'A')
    val offset = codePoint - 0x10000
    append((((offset ushr 10) or 0xD800)).toChar())
    append((((offset and 0x3FF) or 0xDC00)).toChar())
}
