@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.datetime.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.datetime.NameContext
import dev.carcara.kotlinx.locale.datetime.TextStyle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

/**
 * The pattern engine, exposed under the internal-API marker.
 *
 * A source built over CLDR-shaped records needs to render them, and the tests
 * that cover day periods and the all-locales sweep drive the engine directly
 * rather than through whatever pattern a locale happens to use.
 */
@InternalKotlinxLocaleApi
public sealed interface PatternToken {
    public data class Literal(public val text: String) : PatternToken {
        public companion object
    }

    public data class Field(public val letter: Char, public val count: Int) : PatternToken {
        public companion object
    }

    public companion object
}

private val ZONE_FIELD_LETTERS = setOf('z', 'Z', 'v', 'V', 'O', 'x', 'X')

/**
 * Parses a CLDR date/time pattern into tokens. Handles `'quoted literals'` and
 * the `''` escape for a literal apostrophe.
 */
@InternalKotlinxLocaleApi
public fun parseDateTimePattern(pattern: String): List<PatternToken> {
    val tokens = ArrayList<PatternToken>()
    var literal = StringBuilder()

    fun flushLiteral() {
        if (literal.isNotEmpty()) {
            tokens.add(PatternToken.Literal(literal.toString()))
            literal = StringBuilder()
        }
    }

    var i = 0
    while (i < pattern.length) {
        val ch = pattern[i]
        when {
            ch.isAsciiLetter() -> {
                flushLiteral()
                var end = i + 1
                while (end < pattern.length && pattern[end] == ch) end++
                tokens.add(PatternToken.Field(ch, end - i))
                i = end
            }
            ch == '\'' -> {
                if (i + 1 < pattern.length && pattern[i + 1] == '\'') {
                    literal.append('\'')
                    i += 2
                } else {
                    // Quoted section; '' inside it is an escaped apostrophe.
                    i++
                    while (i < pattern.length) {
                        val quoted = pattern[i]
                        if (quoted == '\'') {
                            if (i + 1 < pattern.length && pattern[i + 1] == '\'') {
                                literal.append('\'')
                                i += 2
                            } else {
                                i++
                                break
                            }
                        } else {
                            literal.append(quoted)
                            i++
                        }
                    }
                }
            }
            else -> {
                literal.append(ch)
                i++
            }
        }
    }
    flushLiteral()
    return tokens
}

/**
 * Drops time-zone fields (this library formats zone-less values), swallowing an
 * adjacent whitespace-only literal so patterns like `HH:mm:ss zzzz` degrade to
 * `HH:mm:ss` rather than leaving a dangling space.
 */
@InternalKotlinxLocaleApi
public fun List<PatternToken>.withoutZoneFields(): List<PatternToken> {
    if (none { it is PatternToken.Field && it.letter in ZONE_FIELD_LETTERS }) return this
    val result = ArrayList<PatternToken>(size)
    var trimNextLiteral = false
    for (token in this) {
        when {
            token is PatternToken.Field && token.letter in ZONE_FIELD_LETTERS -> {
                // Swallow the whitespace that separated the zone from the rest:
                // drop a blank literal before it, or trim a non-blank one.
                when (val previous = result.lastOrNull()) {
                    is PatternToken.Literal -> {
                        if (previous.text.isBlank()) {
                            result.removeAt(result.lastIndex)
                        } else if (previous.text != previous.text.trimEnd()) {
                            result[result.lastIndex] = PatternToken.Literal(previous.text.trimEnd())
                        } else {
                            trimNextLiteral = true
                        }
                    }
                    else -> trimNextLiteral = true
                }
            }
            trimNextLiteral && token is PatternToken.Literal -> {
                trimNextLiteral = false
                if (!token.text.isBlank()) {
                    val trimmed = token.text.trimStart()
                    if (trimmed.isNotEmpty()) result.add(PatternToken.Literal(trimmed))
                }
            }
            else -> {
                trimNextLiteral = false
                result.add(token)
            }
        }
    }
    // Bracketed zones ("Bh:mm:ss [zzzz]") leave an empty pair behind; merge
    // adjacent literals and drop those pairs, and any literal left blank by it.
    val merged = ArrayList<PatternToken>(result.size)
    for (token in result) {
        val last = merged.lastOrNull()
        if (token is PatternToken.Literal && last is PatternToken.Literal) {
            merged[merged.lastIndex] = PatternToken.Literal(last.text + token.text)
        } else {
            merged.add(token)
        }
    }
    return merged.mapNotNull { token ->
        if (token !is PatternToken.Literal || !EMPTY_BRACKET_PAIR.containsMatchIn(token.text)) {
            token
        } else {
            token.text.replace(EMPTY_BRACKET_PAIR, "")
                .takeUnless(String::isBlank)
                ?.let { PatternToken.Literal(it) }
        }
    }
}

// The closing ] must be escaped: JS unicode-mode regexes reject a lone one.
private val EMPTY_BRACKET_PAIR = Regex("""\(\s*\)|\[\s*\]""")

/**
 * Renders [tokens] against [date] and [time].
 *
 * [skeletons] carries the quarter names, and is null for everything but skeleton
 * formatting: no standard date or time pattern in any locale uses `Q`, so
 * `kotlinx-locale-datetime-cldr-full` has no use for the names and does not
 * carry them.
 */
@InternalKotlinxLocaleApi
public fun formatPattern(
    tokens: List<PatternToken>,
    data: DateTimeRecord,
    date: LocalDate?,
    time: LocalTime?,
    skeletons: SkeletonRecord? = null,
): String {
    val sb = StringBuilder()
    for (token in tokens) {
        when (token) {
            is PatternToken.Literal -> sb.append(token.text)
            is PatternToken.Field -> formatField(sb, token.letter, token.count, data, date, time, skeletons)
        }
    }
    return sb.toString()
}

private fun formatField(
    sb: StringBuilder,
    letter: Char,
    count: Int,
    data: DateTimeRecord,
    date: LocalDate?,
    time: LocalTime?,
    skeletons: SkeletonRecord?,
) {
    when (letter) {
        'G' -> if (date != null) sb.append(if (date.year > 0) data.era1 else data.era0)
        'y' -> if (date != null) {
            val eraYear = if (date.year > 0) date.year else 1 - date.year
            if (count == 2) {
                sb.appendNumber(eraYear % 100, 2, data.digits)
            } else {
                sb.appendNumber(eraYear, count, data.digits)
            }
        }
        'u' -> if (date != null) sb.appendNumber(date.year, count, data.digits)
        // L is the stand-alone month, M the one that goes inside a date. 110 of
        // CLDR's availableFormats patterns use L, and the skeleton matcher keeps
        // the locale's own letter, so these already reach here and used to render
        // the format name whichever letter was asked for.
        'M', 'L' -> if (date != null) {
            val month = date.month.number
            val context = if (letter == 'L') NameContext.STANDALONE else NameContext.FORMAT
            when (count) {
                1, 2 -> sb.appendNumber(month, count, data.digits)
                3 -> sb.append(data.month(month - 1, TextStyle.ABBREVIATED, context))
                4 -> sb.append(data.month(month - 1, TextStyle.FULL, context))
                else -> sb.append(data.month(month - 1, TextStyle.NARROW, context))
            }
        }
        // Q is the calendar quarter and q its stand-alone form. 41 locales give
        // the two different wide names and 41 different abbreviated ones.
        'Q', 'q' -> if (date != null && skeletons != null) {
            val quarter = (date.month.number - 1) / 3
            val standalone = letter == 'q'
            when (count) {
                1, 2 -> sb.appendNumber(quarter + 1, count, data.digits)
                3 -> sb.append(skeletons.quarterAbbr(quarter, standalone))
                else -> sb.append(skeletons.quarterWide(quarter, standalone))
            }
        }
        'd' -> if (date != null) sb.appendNumber(date.day, count, data.digits)
        'D' -> if (date != null) sb.appendNumber(date.dayOfYear, count, data.digits)
        // c is the stand-alone weekday, E and e the ones inside a date.
        //
        // Note that e and c at counts 1 and 2 are the numeric local day of week
        // in UTS #35, and this renders a name instead. The first day of week that
        // numbering counts from now ships, as `WeekInfo`, so this is a gap rather
        // than a missing table. It wants its own goldens, and so does lifting w,
        // W and F out of the unsupported letters, which need the same data.
        'E', 'e', 'c' -> if (date != null) {
            val index = date.dayOfWeek.isoDayNumber - 1
            val context = if (letter == 'c') NameContext.STANDALONE else NameContext.FORMAT
            when {
                count >= 5 -> sb.append(data.dayOfWeek(index, TextStyle.NARROW, context))
                count == 4 -> sb.append(data.dayOfWeek(index, TextStyle.FULL, context))
                else -> sb.append(data.dayOfWeek(index, TextStyle.ABBREVIATED, context))
            }
        }
        'a' -> if (time != null) sb.append(data.amPm(time))
        'b' -> if (time != null) sb.append(amPmNoonMidnight(time, data))
        'B' -> if (time != null) sb.append(flexibleDayPeriod(time, data))
        'h' -> if (time != null) sb.appendNumber(((time.hour + 11) % 12) + 1, count, data.digits)
        'H' -> if (time != null) sb.appendNumber(time.hour, count, data.digits)
        'K' -> if (time != null) sb.appendNumber(time.hour % 12, count, data.digits)
        'k' -> if (time != null) sb.appendNumber(if (time.hour == 0) 24 else time.hour, count, data.digits)
        'm' -> if (time != null) sb.appendNumber(time.minute, count, data.digits)
        's' -> if (time != null) sb.appendNumber(time.second, count, data.digits)
        // Unsupported fields (week numbers, fractional seconds, ...) are omitted
        // rather than crashing; they do not occur in CLDR standard style patterns.
        else -> {}
    }
}

private fun DateTimeRecord.amPm(time: LocalTime): String = if (time.hour < 12) am else pm

/**
 * The `b` field: AM/PM, except that exactly 00:00:00 and 12:00:00 use the
 * locale's midnight/noon names when it has them.
 */
private fun amPmNoonMidnight(time: LocalTime, data: DateTimeRecord): String {
    if (time.minute == 0 && time.second == 0) {
        if (time.hour == 0) data.dayPeriodName(DayPeriodCodes.MIDNIGHT)?.let { return it }
        if (time.hour == 12) data.dayPeriodName(DayPeriodCodes.NOON)?.let { return it }
    }
    return data.amPm(time)
}

/**
 * The `B` field: the flexible day period ("in the afternoon", 晚上) selected by
 * the locale's CLDR day period rules. Midnight/noon point rules only apply at
 * the exact time (zero seconds); a period the locale has no name for falls
 * back to AM/PM, per UTS #35.
 */
private fun flexibleDayPeriod(time: LocalTime, data: DateTimeRecord): String {
    val minuteOfDay = time.hour * 60 + time.minute
    for (rule in data.dayPeriodRules) {
        val matches = when {
            rule.isPoint -> minuteOfDay == rule.start && time.second == 0
            rule.start < rule.end -> minuteOfDay >= rule.start && minuteOfDay < rule.end
            else -> minuteOfDay >= rule.start || minuteOfDay < rule.end
        }
        if (matches) return data.dayPeriodName(rule.code) ?: data.amPm(time)
    }
    return data.amPm(time)
}

private fun StringBuilder.appendNumber(value: Int, minWidth: Int, digits: String) {
    if (value < 0) {
        append('-')
        appendNumber(-value, minWidth, digits)
        return
    }
    val text = value.toString()
    repeat(minWidth - text.length) { appendDigit(0, digits) }
    for (ch in text) appendDigit(ch - '0', digits)
}

/**
 * Appends one digit of a numbering system.
 *
 * Not `digits[index]`. Chakma, and every other numbering system whose digits sit
 * outside the basic plane, stores each digit as a surrogate pair, so its ten
 * digits are twenty characters and indexing by one lands halfway through a pair.
 * The result is a replacement character wherever a number should be.
 */
private fun StringBuilder.appendDigit(index: Int, digits: String) {
    if (digits.length == 20) append(digits, index * 2, index * 2 + 2) else append(digits[index])
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
