package dev.srsouza.kotlinx.datetime.locale.internal

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

internal sealed interface PatternToken {
    data class Literal(val text: String) : PatternToken
    data class Field(val letter: Char, val count: Int) : PatternToken
}

private val ZONE_FIELD_LETTERS = setOf('z', 'Z', 'v', 'V', 'O', 'x', 'X')

/**
 * Parses a CLDR date/time pattern into tokens. Handles `'quoted literals'` and
 * the `''` escape for a literal apostrophe.
 */
internal fun parseDateTimePattern(pattern: String): List<PatternToken> {
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
internal fun List<PatternToken>.withoutZoneFields(): List<PatternToken> {
    if (none { it is PatternToken.Field && it.letter in ZONE_FIELD_LETTERS }) return this
    val result = ArrayList<PatternToken>(size)
    var skipNextWhitespace = false
    for (token in this) {
        when {
            token is PatternToken.Field && token.letter in ZONE_FIELD_LETTERS -> {
                val previous = result.lastOrNull()
                if (previous is PatternToken.Literal && previous.text.isBlank()) {
                    result.removeAt(result.lastIndex)
                } else {
                    skipNextWhitespace = true
                }
            }
            skipNextWhitespace && token is PatternToken.Literal && token.text.isBlank() ->
                skipNextWhitespace = false
            else -> {
                skipNextWhitespace = false
                result.add(token)
            }
        }
    }
    return result
}

internal fun formatPattern(
    tokens: List<PatternToken>,
    data: LocaleData,
    date: LocalDate?,
    time: LocalTime?,
): String {
    val sb = StringBuilder()
    for (token in tokens) {
        when (token) {
            is PatternToken.Literal -> sb.append(token.text)
            is PatternToken.Field -> formatField(sb, token.letter, token.count, data, date, time)
        }
    }
    return sb.toString()
}

private fun formatField(
    sb: StringBuilder,
    letter: Char,
    count: Int,
    data: LocaleData,
    date: LocalDate?,
    time: LocalTime?,
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
        'M', 'L' -> if (date != null) {
            val month = date.month.number
            when (count) {
                1, 2 -> sb.appendNumber(month, count, data.digits)
                3 -> sb.append(data.monthsAbbr[month - 1])
                4 -> sb.append(data.monthsWide[month - 1])
                else -> sb.append(data.monthsNarrow[month - 1])
            }
        }
        'd' -> if (date != null) sb.appendNumber(date.day, count, data.digits)
        'D' -> if (date != null) sb.appendNumber(date.dayOfYear, count, data.digits)
        'E', 'e', 'c' -> if (date != null) {
            val index = date.dayOfWeek.isoDayNumber - 1
            when {
                count >= 5 -> sb.append(data.daysNarrow[index])
                count == 4 -> sb.append(data.daysWide[index])
                else -> sb.append(data.daysAbbr[index])
            }
        }
        'a', 'b', 'B' -> if (time != null) sb.append(if (time.hour < 12) data.am else data.pm)
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

private fun StringBuilder.appendNumber(value: Int, minWidth: Int, digits: String) {
    if (value < 0) {
        append('-')
        appendNumber(-value, minWidth, digits)
        return
    }
    val text = value.toString()
    repeat(minWidth - text.length) { append(digits[0]) }
    for (ch in text) append(digits[ch - '0'])
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
