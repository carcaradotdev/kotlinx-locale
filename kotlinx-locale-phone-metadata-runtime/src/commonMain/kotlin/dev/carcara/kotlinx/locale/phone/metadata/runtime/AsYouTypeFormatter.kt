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

package dev.carcara.kotlinx.locale.phone.metadata.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.country.Country

/**
 * Formats a number as it is being typed.
 *
 * The one part of this domain that cannot be a pure function of a finished
 * number, because it is asked the same question repeatedly with more of the
 * answer each time. `020`, `0207`, `02071` and `020713` are four different
 * questions and only the last two have a grouping that will survive.
 *
 * Kept as an explicit object with [append] and [clear] rather than a
 * `format(prefix)` function, which is the same shape libphonenumber uses. The
 * reason is not convenience: choosing a grouping means walking every format rule
 * the territory has, and a text field that reformatted from scratch on every
 * keystroke would do that work once per character rather than once per number.
 *
 * ## What it will not do
 *
 * It will not tell you where to put the caret. A field that formats while
 * someone types has to map a position in the digits onto a position in the
 * formatted text, and that mapping depends on the editor rather than on the
 * number, so it is the caller's. [digitsBefore] is the piece of it this can
 * answer: how many digits precede a given offset in the output.
 */
public class AsYouTypeFormatter internal constructor(
    private val territory: PhoneTerritoryRecord?,
    private val formats: List<PhoneFormatRule>,
) {

    private val digits = StringBuilder()
    private var formatted: String = ""

    /** Every digit accepted so far, in order, with no punctuation. */
    public val nationalDigits: String get() = digits.toString()

    /** The digits formatted as far as they can be, which is the field's contents. */
    public val text: String get() = formatted

    /** Adds one character, ignoring anything that is not a digit. Returns [text]. */
    public fun append(ch: Char): String {
        if (ch.isDigit()) {
            digits.append(ch)
            formatted = render()
        }
        return formatted
    }

    /** Adds every digit in [input]. Returns [text]. */
    public fun append(input: CharSequence): String {
        for (ch in input) if (ch.isDigit()) digits.append(ch)
        formatted = render()
        return formatted
    }

    /** Removes the last digit, which is what a backspace means. Returns [text]. */
    public fun removeLast(): String {
        if (digits.isNotEmpty()) digits.deleteAt(digits.length - 1)
        formatted = render()
        return formatted
    }

    /** Forgets everything typed so far. */
    public fun clear() {
        digits.clear()
        formatted = ""
    }

    /**
     * How many digits appear before [offset] in [text].
     *
     * The half of caret placement that belongs to the number: an editor that
     * knows the caret sat after the nth digit can find where the nth digit
     * landed after reformatting.
     */
    public fun digitsBefore(offset: Int): Int = text.take(offset).count(Char::isDigit)

    /**
     * The digits grouped by the first rule that can still apply.
     *
     * "Can still apply" rather than "applies": part way through a number no rule
     * matches yet, so the rule is chosen on its leading digits and the grouping
     * is drawn by consuming the pattern's digit runs. A number with no rule that
     * fits comes back as bare digits, which is what a field should show rather
     * than a grouping that is about to be wrong.
     */
    private fun render(): String {
        val typed = digits.toString()
        if (typed.isEmpty()) return ""
        val national = territory?.nationalPrefix
        // The national prefix is shown as typed and is not part of what the
        // format rules describe, so it is held aside and put back afterwards.
        val hasPrefix = national != null && typed.startsWith(national) && typed.length > national.length
        val body = if (hasPrefix) typed.substring(national.length) else typed

        val rule = formats.firstOrNull { it.acceptsPrefix(body, body.length - 1) && it.canGroup(body) }
            ?: return typed
        val grouped = rule.groupPrefix(body) ?: return typed
        return if (hasPrefix) national + grouped else grouped
    }

    public companion object
}

/**
 * True when this rule's pattern could still describe a number starting with
 * [prefix].
 *
 * The pattern is anchored and [prefix] is incomplete, so a full match is the
 * wrong question. What is asked instead is whether the digit runs the pattern
 * declares can absorb this many digits.
 */
internal fun PhoneFormatRule.canGroup(prefix: String): Boolean {
    val runs = digitRuns() ?: return false
    return prefix.length <= runs.sum()
}

/**
 * [prefix] split into this rule's groups and joined by its format string.
 *
 * Only the groups that are complete are written, plus whatever part of the next
 * one has arrived, so `0207` under `(\d{3})(\d{4})(\d{4})` reads `020 7`.
 */
internal fun PhoneFormatRule.groupPrefix(prefix: String): String? {
    val runs = digitRuns() ?: return null
    val pieces = ArrayList<String>(runs.size)
    var at = 0
    for (run in runs) {
        if (at >= prefix.length) break
        val end = minOf(at + run, prefix.length)
        pieces += prefix.substring(at, end)
        at = end
    }
    if (at < prefix.length) return null
    // The format string with each `$n` replaced by the piece that arrived, and
    // everything from the first missing piece onward dropped along with the
    // punctuation that would have introduced it.
    return buildString {
        var index = 0
        while (index < format.length) {
            val ch = format[index]
            if (ch == '$' && index + 1 < format.length && format[index + 1].isDigit()) {
                val group = format[index + 1] - '1'
                if (group >= pieces.size) return@buildString
                append(pieces[group])
                index += 2
            } else {
                append(ch)
                index++
            }
        }
    }.trimEnd { !it.isDigit() }
}

/**
 * The fixed digit-run lengths this rule's pattern declares, or `null`.
 *
 * `(\d{3})(\d{4})` is three then four. A rule whose groups are variable, written
 * `{3,4}`, has no single grouping to draw part way through and is skipped: a
 * field that regrouped as the range resolved would move digits under the caret.
 */
internal fun PhoneFormatRule.digitRuns(): List<Int>? {
    val cached = cachedRuns
    if (cached != null) return cached.takeIf { it.isNotEmpty() }
    val runs = ArrayList<Int>()
    val source = patternText
    var index = 0
    var variable = false
    while (index < source.length) {
        when {
            source.startsWith("""(\d{""", index) -> {
                val close = source.indexOf('}', index)
                if (close < 0) return null
                val body = source.substring(index + 4, close)
                if (',' in body) variable = true
                runs += body.substringBefore(',').toIntOrNull() ?: return null
                index = close + 1
            }
            source.startsWith("""(\d)""", index) -> {
                runs += 1
                index += 4
            }
            else -> index++
        }
    }
    val result = if (variable) emptyList() else runs
    cachedRuns = result
    return result.takeIf { it.isNotEmpty() }
}

/** Opens an as-you-type formatter for [region]. */
public fun PayloadPhoneNumbers.asYouType(region: Country): AsYouTypeFormatter = asYouTypeFor(region.name)
