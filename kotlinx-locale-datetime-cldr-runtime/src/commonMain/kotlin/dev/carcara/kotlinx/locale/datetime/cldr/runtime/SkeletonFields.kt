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

package dev.carcara.kotlinx.locale.datetime.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

/**
 * The sixteen UTS #35 date fields.
 *
 * The order is the specification's and is load-bearing twice over: it is the
 * order a canonical skeleton writes its fields in, whatever order the caller
 * asked in, and it is the index the append formats and field display names in a
 * skeleton record are stored against. `DATE_FIELD_TYPES` in `:codegen` mirrors
 * it, the way `DAY_PERIOD_TYPES` and [DayPeriodCodes] already mirror each other.
 */
internal object SkeletonField {
    const val ERA: Int = 0
    const val YEAR: Int = 1
    const val QUARTER: Int = 2
    const val MONTH: Int = 3
    const val WEEK_OF_YEAR: Int = 4
    const val WEEK_OF_MONTH: Int = 5
    const val WEEKDAY: Int = 6
    const val DAY: Int = 7
    const val DAY_OF_YEAR: Int = 8
    const val DAY_OF_WEEK_IN_MONTH: Int = 9
    const val DAYPERIOD: Int = 10
    const val HOUR: Int = 11
    const val MINUTE: Int = 12
    const val SECOND: Int = 13
    const val FRACTIONAL_SECOND: Int = 14
    const val ZONE: Int = 15
    const val COUNT: Int = 16

    /** Everything below [DAYPERIOD]; a day period counts as a time field. */
    const val DATE_MASK: Int = (1 shl DAYPERIOD) - 1
    const val TIME_MASK: Int = (1 shl COUNT) - 1 - DATE_MASK
}

/**
 * Field weights, whose differences are the distance metric.
 *
 * A positive weight is a numeric field and a negative one is text, so crossing
 * between them costs more than any width change within one. Text widths sit one
 * apart, so neighbouring widths are cheap. [DELTA] separates sibling letters for
 * the same field — `M` from stand-alone `L`, `E` from `c`, `h` from `K` — and is
 * larger than any width difference, so a width change is always preferred to
 * changing which letter renders the field.
 */
private const val DELTA = 0x10
private const val NUMERIC = 0x100
private const val NARROW = -0x101
private const val SHORTER = -0x102
private const val SHORT = -0x103
private const val LONG = -0x104

/**
 * The empty slot in a [SkeletonFields] reduction.
 *
 * NUL rather than a space because the slot marker has to sort below every
 * pattern letter for [SkeletonFields.compareTo] to order reductions the way
 * ICU's candidate map is keyed.
 */
private const val ABSENT = '\u0000'

/** A field the candidate has and the request did not ask for. */
internal const val EXTRA_FIELD: Int = 0x10000

/** A field the request asked for and the candidate does not have. */
internal const val MISSING_FIELD: Int = 0x1000

/**
 * One row of the field table: a pattern letter over a range of repeat counts.
 *
 * Numeric letters have a single row spanning their whole range and take their
 * width from the count at parse time; text letters have one row per width and
 * carry it in [weight].
 */
private class FieldRow(val letter: Char, val field: Int, val weight: Int, val min: Int, val max: Int = min)

/**
 * Every pattern letter UTS #35 defines, ported from ICU's `types` table.
 *
 * Order matters: [canonicalCharFor] takes the first row for a field, which is
 * what makes `G y Q M w W E d D F a H m s S v` the canonical letters.
 */
private val FIELD_ROWS: List<FieldRow> = listOf(
    FieldRow('G', SkeletonField.ERA, SHORT, 1, 3),
    FieldRow('G', SkeletonField.ERA, LONG, 4),
    FieldRow('G', SkeletonField.ERA, NARROW, 5),

    FieldRow('y', SkeletonField.YEAR, NUMERIC, 1, 20),
    FieldRow('Y', SkeletonField.YEAR, NUMERIC + DELTA, 1, 20),
    FieldRow('u', SkeletonField.YEAR, NUMERIC + 2 * DELTA, 1, 20),
    FieldRow('r', SkeletonField.YEAR, NUMERIC + 3 * DELTA, 1, 20),
    FieldRow('U', SkeletonField.YEAR, SHORT, 1, 3),
    FieldRow('U', SkeletonField.YEAR, LONG, 4),
    FieldRow('U', SkeletonField.YEAR, NARROW, 5),

    FieldRow('Q', SkeletonField.QUARTER, NUMERIC, 1, 2),
    FieldRow('Q', SkeletonField.QUARTER, SHORT, 3),
    FieldRow('Q', SkeletonField.QUARTER, LONG, 4),
    FieldRow('Q', SkeletonField.QUARTER, NARROW, 5),
    FieldRow('q', SkeletonField.QUARTER, NUMERIC + DELTA, 1, 2),
    FieldRow('q', SkeletonField.QUARTER, SHORT - DELTA, 3),
    FieldRow('q', SkeletonField.QUARTER, LONG - DELTA, 4),
    FieldRow('q', SkeletonField.QUARTER, NARROW - DELTA, 5),

    FieldRow('M', SkeletonField.MONTH, NUMERIC, 1, 2),
    FieldRow('M', SkeletonField.MONTH, SHORT, 3),
    FieldRow('M', SkeletonField.MONTH, LONG, 4),
    FieldRow('M', SkeletonField.MONTH, NARROW, 5),
    FieldRow('L', SkeletonField.MONTH, NUMERIC + DELTA, 1, 2),
    FieldRow('L', SkeletonField.MONTH, SHORT - DELTA, 3),
    FieldRow('L', SkeletonField.MONTH, LONG - DELTA, 4),
    FieldRow('L', SkeletonField.MONTH, NARROW - DELTA, 5),
    FieldRow('l', SkeletonField.MONTH, NUMERIC + DELTA, 1, 1),

    FieldRow('w', SkeletonField.WEEK_OF_YEAR, NUMERIC, 1, 2),
    FieldRow('W', SkeletonField.WEEK_OF_MONTH, NUMERIC, 1, 1),

    FieldRow('E', SkeletonField.WEEKDAY, SHORT, 1, 3),
    FieldRow('E', SkeletonField.WEEKDAY, LONG, 4),
    FieldRow('E', SkeletonField.WEEKDAY, NARROW, 5),
    FieldRow('E', SkeletonField.WEEKDAY, SHORTER, 6),
    FieldRow('c', SkeletonField.WEEKDAY, NUMERIC + 2 * DELTA, 1, 2),
    FieldRow('c', SkeletonField.WEEKDAY, SHORT - 2 * DELTA, 3),
    FieldRow('c', SkeletonField.WEEKDAY, LONG - 2 * DELTA, 4),
    FieldRow('c', SkeletonField.WEEKDAY, NARROW - 2 * DELTA, 5),
    FieldRow('c', SkeletonField.WEEKDAY, SHORTER - 2 * DELTA, 6),
    FieldRow('e', SkeletonField.WEEKDAY, NUMERIC + DELTA, 1, 2),
    FieldRow('e', SkeletonField.WEEKDAY, SHORT - DELTA, 3),
    FieldRow('e', SkeletonField.WEEKDAY, LONG - DELTA, 4),
    FieldRow('e', SkeletonField.WEEKDAY, NARROW - DELTA, 5),
    FieldRow('e', SkeletonField.WEEKDAY, SHORTER - DELTA, 6),

    FieldRow('d', SkeletonField.DAY, NUMERIC, 1, 2),
    FieldRow('g', SkeletonField.DAY, NUMERIC + DELTA, 1, 20),
    FieldRow('D', SkeletonField.DAY_OF_YEAR, NUMERIC, 1, 3),
    FieldRow('F', SkeletonField.DAY_OF_WEEK_IN_MONTH, NUMERIC, 1, 1),

    FieldRow('a', SkeletonField.DAYPERIOD, SHORT, 1, 3),
    FieldRow('a', SkeletonField.DAYPERIOD, LONG, 4),
    FieldRow('a', SkeletonField.DAYPERIOD, NARROW, 5),
    FieldRow('b', SkeletonField.DAYPERIOD, SHORT - DELTA, 1, 3),
    FieldRow('b', SkeletonField.DAYPERIOD, LONG - DELTA, 4),
    FieldRow('b', SkeletonField.DAYPERIOD, NARROW - DELTA, 5),
    // b sits one delta from a and B three, so that a request for b lands on an
    // a pattern rather than on a flexible day period.
    FieldRow('B', SkeletonField.DAYPERIOD, SHORT - 3 * DELTA, 1, 3),
    FieldRow('B', SkeletonField.DAYPERIOD, LONG - 3 * DELTA, 4),
    FieldRow('B', SkeletonField.DAYPERIOD, NARROW - 3 * DELTA, 5),

    FieldRow('H', SkeletonField.HOUR, NUMERIC + 10 * DELTA, 1, 2),
    FieldRow('k', SkeletonField.HOUR, NUMERIC + 11 * DELTA, 1, 2),
    FieldRow('h', SkeletonField.HOUR, NUMERIC, 1, 2),
    FieldRow('K', SkeletonField.HOUR, NUMERIC + DELTA, 1, 2),

    FieldRow('m', SkeletonField.MINUTE, NUMERIC, 1, 2),
    FieldRow('s', SkeletonField.SECOND, NUMERIC, 1, 2),
    FieldRow('A', SkeletonField.SECOND, NUMERIC + DELTA, 1, 1000),
    FieldRow('S', SkeletonField.FRACTIONAL_SECOND, NUMERIC, 1, 1000),

    FieldRow('v', SkeletonField.ZONE, SHORT - 2 * DELTA, 1, 1),
    FieldRow('v', SkeletonField.ZONE, LONG - 2 * DELTA, 4),
    FieldRow('z', SkeletonField.ZONE, SHORT, 1, 3),
    FieldRow('z', SkeletonField.ZONE, LONG, 4),
    FieldRow('Z', SkeletonField.ZONE, NARROW - DELTA, 1, 3),
    FieldRow('Z', SkeletonField.ZONE, LONG - DELTA, 4),
    FieldRow('Z', SkeletonField.ZONE, SHORT - DELTA, 5),
    FieldRow('O', SkeletonField.ZONE, SHORT - DELTA, 1, 1),
    FieldRow('O', SkeletonField.ZONE, LONG - DELTA, 4),
    FieldRow('V', SkeletonField.ZONE, SHORT - DELTA, 1, 1),
    FieldRow('V', SkeletonField.ZONE, LONG - DELTA, 2),
    FieldRow('V', SkeletonField.ZONE, LONG - 1 - DELTA, 3),
    FieldRow('V', SkeletonField.ZONE, LONG - 2 - DELTA, 4),
    FieldRow('X', SkeletonField.ZONE, NARROW - DELTA, 1, 1),
    FieldRow('X', SkeletonField.ZONE, SHORT - DELTA, 2),
    FieldRow('X', SkeletonField.ZONE, LONG - DELTA, 4),
    FieldRow('x', SkeletonField.ZONE, NARROW - DELTA, 1, 1),
    FieldRow('x', SkeletonField.ZONE, SHORT - DELTA, 2),
    FieldRow('x', SkeletonField.ZONE, LONG - DELTA, 4),
)

/**
 * The row for a run of [count] copies of [letter], or `null` when the letter is
 * not a field at all.
 *
 * A count outside every row's range falls back to the letter's last row rather
 * than failing, so `MMMMMM` reads as a narrow month the way ICU reads it.
 */
private fun rowFor(letter: Char, count: Int): FieldRow? {
    var fallback: FieldRow? = null
    for (row in FIELD_ROWS) {
        if (row.letter != letter) continue
        fallback = row
        if (row.min <= count && count <= row.max) return row
    }
    return fallback
}

/** The letter a canonical skeleton writes [field] with; `h` and `K` both keep `h`. */
private fun canonicalCharFor(field: Int, reference: Char): Char {
    if (reference == 'h' || reference == 'K') return 'h'
    return FIELD_ROWS.first { it.field == field }.letter
}

/** What a run of [count] copies of [letter] is: which field, rendered how. */
internal class FieldInfo(val field: Int, val isNumeric: Boolean)

/** [FieldInfo] for a run, or `null` when the letter names no field. */
internal fun fieldInfoFor(letter: Char, count: Int): FieldInfo? = rowFor(letter, count)?.let { FieldInfo(it.field, it.weight > 0) }

/**
 * A skeleton reduced to its fields: which letter at which width, per field.
 *
 * This is what both a request and a candidate become before they are compared,
 * and it is why the order the caller writes a skeleton in does not matter —
 * fields land in slots. Literal text is dropped, so `dd/MM` and `MM-dd` reduce
 * to the same thing.
 */
internal class SkeletonFields {
    val chars: CharArray = CharArray(SkeletonField.COUNT)
    val lengths: IntArray = IntArray(SkeletonField.COUNT)

    /** Field weights, or 0 where the field is absent. */
    val weights: IntArray = IntArray(SkeletonField.COUNT)

    /**
     * Whether the AM/PM field was inserted rather than asked for, which happens
     * for a twelve-hour request that did not mention one. It counts for matching
     * but is hidden from the skeleton this reads back as.
     */
    var dayPeriodWasImplied: Boolean = false
        private set

    /** A bit per field present, for the date/time split and for the append loop. */
    fun fieldMask(): Int {
        var mask = 0
        for (i in 0 until SkeletonField.COUNT) if (weights[i] != 0) mask = mask or (1 shl i)
        return mask
    }

    fun isNumeric(field: Int): Boolean = weights[field] > 0

    /**
     * The base skeleton: the same fields at their letter's smallest width, with
     * `G E z v Q` flattened to one.
     *
     * Two patterns sharing one of these are two spellings of the same request —
     * a locale's LONG and FULL time patterns usually differ only in how wide the
     * zone is — and only the first of them is worth keeping as a candidate.
     */
    fun basePattern(): String = buildString {
        for (i in 0 until SkeletonField.COUNT) {
            val ch = chars[i]
            if (ch == ' ') continue
            val row = rowFor(ch, lengths[i]) ?: continue
            val count = if (ch in "GEzvQ") 1 else row.min
            repeat(count) { append(ch) }
        }
    }

    /** The canonical skeleton: canonical letters, requested widths, field order. */
    fun canonicalString(): String = buildString {
        for (i in 0 until SkeletonField.COUNT) {
            val ch = chars[i]
            if (ch == ' ') continue
            if (i == SkeletonField.DAYPERIOD && dayPeriodWasImplied) continue
            repeat(lengths[i]) { append(canonicalCharFor(i, ch)) }
        }
    }

    /**
     * Orders two reductions the way ICU's candidate map is keyed, so that a tie
     * on both distance and missing fields resolves the same way it does there.
     */
    fun compareTo(other: SkeletonFields): Int {
        for (i in 0 until SkeletonField.COUNT) {
            val byChar = chars[i].compareTo(other.chars[i])
            if (byChar != 0) return byChar
            val byLength = lengths[i].compareTo(other.lengths[i])
            if (byLength != 0) return byLength
        }
        return 0
    }

    /**
     * A key that is equal exactly when two reductions are the same request.
     *
     * The canonical skeleton will not do: it spells `M` and stand-alone `L` the
     * same way, and the pool has to tell those apart.
     */
    fun identity(): String = buildString(SkeletonField.COUNT * 2) {
        for (i in 0 until SkeletonField.COUNT) {
            append(chars[i])
            append(lengths[i].toChar())
        }
    }

    private fun put(field: Int, letter: Char, length: Int, weight: Int) {
        chars[field] = letter
        lengths[field] = length
        weights[field] = weight
    }

    private fun clear(field: Int) {
        chars[field] = ' '
        lengths[field] = 0
        weights[field] = 0
    }

    companion object {

        /**
         * Reduces [pattern] to its fields.
         *
         * Returns `null` when the pattern uses a letter that is not a field, or
         * names one field twice — the `r`/`y` and `r`/`U` pairing ICU tolerates
         * included, since a related-gregorian year alongside a calendar year is
         * one field asked for two ways rather than a contradiction.
         */
        fun of(pattern: String): SkeletonFields? {
            val fields = SkeletonFields()
            for (token in parseDateTimePattern(pattern)) {
                if (token !is PatternToken.Field) continue
                val row = rowFor(token.letter, token.count) ?: return null
                val existing = fields.chars[row.field]
                if (existing != ' ') {
                    val tolerated = (existing == 'r' && (token.letter == 'U' || token.letter == 'y')) ||
                        ((existing == 'U' || existing == 'y') && token.letter == 'r')
                    if (tolerated) continue
                    return null
                }
                // A numeric field takes its width from the run; a text one from
                // the row it matched.
                val weight = if (row.weight > 0) row.weight + token.count else row.weight
                fields.put(row.field, token.letter, token.count, weight)
            }
            fields.applyHourConventions()
            return fields
        }
    }

    /**
     * The two things UTS #35 says an hour field implies about day periods: a
     * twelve-hour request needs one even when unwritten, and a twenty-four-hour
     * one cannot have it.
     *
     * Applied before matching rather than after, so that asking for `hm` scores
     * against `h:mm a` as the exact cover it is.
     */
    private fun applyHourConventions() {
        val hour = chars[SkeletonField.HOUR]
        if (hour == ' ') return
        if (hour == 'h' || hour == 'K') {
            if (chars[SkeletonField.DAYPERIOD] == ' ') {
                val row = FIELD_ROWS.first { it.field == SkeletonField.DAYPERIOD }
                put(SkeletonField.DAYPERIOD, row.letter, row.min, row.weight)
                dayPeriodWasImplied = true
            }
        } else if (chars[SkeletonField.DAYPERIOD] != ' ') {
            clear(SkeletonField.DAYPERIOD)
        }
    }
}

/**
 * How far [this] request is from [candidate], and which fields differ.
 *
 * A field neither has costs nothing, an identical one costs nothing, and
 * everything else is the weight gap — small within a width, larger across
 * sibling letters, larger again across the numeric/text line, and larger than
 * all of those for a field one of them simply does not have. [includeMask]
 * narrows which of the request's fields count, which is how the append loop asks
 * "what covers the part still missing".
 */
internal fun SkeletonFields.distanceTo(candidate: SkeletonFields, includeMask: Int, into: FieldDifference): Int {
    var total = 0
    into.clear()
    for (i in 0 until SkeletonField.COUNT) {
        val mine = if (includeMask and (1 shl i) == 0) 0 else weights[i]
        val theirs = candidate.weights[i]
        when {
            mine == theirs -> {}
            mine == 0 -> {
                total += EXTRA_FIELD
                into.extra = into.extra or (1 shl i)
            }
            theirs == 0 -> {
                total += MISSING_FIELD
                into.missing = into.missing or (1 shl i)
            }
            else -> total += if (mine > theirs) mine - theirs else theirs - mine
        }
    }
    return total
}

/** Which fields a comparison found missing from, or surplus to, the request. */
internal class FieldDifference {
    var missing: Int = 0
    var extra: Int = 0

    fun clear() {
        missing = 0
        extra = 0
    }

    fun copyFrom(other: FieldDifference) {
        missing = other.missing
        extra = other.extra
    }
}

/** The highest field index in [mask], which names the append format to use. */
internal fun topFieldOf(mask: Int): Int {
    var remaining = mask
    var index = 0
    while (remaining != 0) {
        remaining = remaining ushr 1
        index++
    }
    return index - 1
}
