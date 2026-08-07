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
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource
import dev.carcara.kotlinx.locale.internal.ENTRY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.KEY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.resolvedRecord
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * A source of date and time interval formatting.
 *
 * An interval is not two formatted values with a dash between them. CLDR gives
 * each locale a pattern per skeleton per greatest-difference field, so English
 * writes `Jul 18 – 22, 2026` rather than `Jul 18, 2026 – Jul 22, 2026`, and the
 * parts that both ends share appear once.
 */
public interface IntervalFormatSource : LocaleDataSource {

    /** The two dates as one interval, or null when this build carries nothing for [locale]. */
    public fun intervalFormatOrNull(start: LocalDate, end: LocalDate, skeleton: String, locale: Locale): String?

    public fun intervalFormatOrNull(start: LocalTime, end: LocalTime, skeleton: String, locale: Locale): String?

    public fun intervalFormatOrNull(start: LocalDateTime, end: LocalDateTime, skeleton: String, locale: Locale): String?

    public companion object
}

/**
 * Which CLDR field two values first differ in, largest first, or null when they
 * are equal in every field an interval pattern can name.
 *
 * The hour is reported as `h` and written back out as whichever of `H` or `h`
 * the locale's own pattern uses, because CLDR keys the two separately.
 */
private fun greatestDifference(startDate: LocalDate?, endDate: LocalDate?, startTime: LocalTime?, endTime: LocalTime?): Char? {
    if (startDate != null && endDate != null) {
        if (startDate.year != endDate.year) return 'y'
        if (startDate.month != endDate.month) return 'M'
        if (startDate.day != endDate.day) return 'd'
    }
    if (startTime != null && endTime != null) {
        if ((startTime.hour < 12) != (endTime.hour < 12)) return 'a'
        if (startTime.hour != endTime.hour) return 'h'
        if (startTime.minute != endTime.minute) return 'm'
        if (startTime.second != endTime.second) return 's'
    }
    return null
}

/** The fields an interval pattern can name, coarsest first. */
private const val FIELD_ORDER = "yMdahms"

/**
 * Where a pattern letter sits in [FIELD_ORDER], or -1 for a letter that names no
 * field an interval turns on.
 *
 * The synonyms matter. A skeleton can reach the day through `d`, `E` or `c`, and
 * the hour through any of four letters depending on the locale's cycle, and a
 * comparison that only knew the canonical spelling would decide a skeleton had
 * no day field because the locale wrote a weekday.
 */
private fun fieldRank(letter: Char): Int = when (letter) {
    'y', 'Y', 'u', 'U', 'r' -> 0
    'M', 'L' -> 1
    'd', 'D', 'E', 'e', 'c' -> 2
    'a', 'b', 'B' -> 3
    'h', 'H', 'K', 'k' -> 4
    'm' -> 5
    's' -> 6
    else -> -1
}

/**
 * [requested] widened to reach [difference], or null when it already does.
 *
 * Every field between the one the two values differ in and the coarsest the
 * skeleton names, so a day over two years widens to a whole date rather than to
 * a year and a day with a month-shaped hole between them. Existing letters keep
 * their widths; only the missing fields are added, at their narrowest, which is
 * what CLDR's own fallback does.
 */
private fun widenedSkeleton(requested: String, difference: Char): String? {
    val target = fieldRank(difference)
    if (target < 0) return null
    val coarsest = requested.mapNotNull { letter -> fieldRank(letter).takeIf { it >= 0 } }.minOrNull() ?: return null
    if (target >= coarsest) return null
    return FIELD_ORDER.substring(target, coarsest) + requested
}

/**
 * Interval formatting over a table of CLDR interval records.
 *
 * Takes the skeleton source rather than its tables, because an interval is a
 * split of the pattern that source's matcher picks. Sharing the object shares
 * one matcher pool per locale, and building one sorts the locale's whole
 * candidate list.
 */
@InternalKotlinxLocaleApi
public class PayloadIntervalFormats(private val records: Map<String, String>, private val skeletons: PayloadSkeletonFormats) :
    IntervalFormatSource {

    override val supportedLocales: Set<Locale> by lazy { supportedLocalesOf(records) }

    private val decoded = HashMap<String, IntervalRecord?>()

    override fun intervalFormatOrNull(start: LocalDate, end: LocalDate, skeleton: String, locale: Locale): String? =
        render(skeleton, locale, startDate = start, endDate = end, startTime = null, endTime = null)

    override fun intervalFormatOrNull(start: LocalTime, end: LocalTime, skeleton: String, locale: Locale): String? =
        render(skeleton, locale, startDate = null, endDate = null, startTime = start, endTime = end)

    override fun intervalFormatOrNull(start: LocalDateTime, end: LocalDateTime, skeleton: String, locale: Locale): String? = render(
        skeleton,
        locale,
        startDate = start.date,
        endDate = end.date,
        startTime = start.time,
        endTime = end.time,
    )

    private fun render(
        skeleton: String,
        locale: Locale,
        startDate: LocalDate?,
        endDate: LocalDate?,
        startTime: LocalTime?,
        endTime: LocalTime?,
    ): String? {
        val locales = skeletons.skeletonsFor(locale) ?: return null
        val pattern = locales.matcher.bestPatternOrNull(skeleton) ?: return null

        fun formatOne(date: LocalDate?, time: LocalTime?): String {
            val tokens = parseDateTimePattern(pattern).withoutZoneFields()
            return formatPattern(tokens, locales.dateTime, date, time, locales.record)
        }

        val difference = greatestDifference(startDate, endDate, startTime, endTime)
        // Equal in every field the interval could name: CLDR's answer is the
        // plain pattern once, not the same text twice with a dash in it.
        if (difference == null) return formatOne(startDate, startTime)

        val record = recordFor(locale) ?: return null
        // Keyed off the request rather than off the matched pattern. CLDR's
        // interval ids are skeletons, so `yMd` is the key; the pattern the
        // matcher returns is `y-MM-dd` in Afrikaans, whose canonical form
        // carries the field widths and matches nothing.
        //
        // `j`, `J` and `C` are the exception, because they are requests for
        // whichever hour the locale prefers and CLDR keys the twelve- and
        // twenty-four-hour entries apart. Only the resolved pattern knows which
        // one this locale reached for, so they are rewritten before the lookup.
        val hourLetter = pattern.firstOrNull { it in "HhKk" } ?: 'H'
        val requested = skeleton.map { if (it in "jJC") hourLetter else it }.joinToString("")
        val canonical = SkeletonFields.of(requested)?.canonicalString()
        val intervalPattern = canonical?.let { record.patternFor(it, difference, pattern) }

        if (intervalPattern != null) {
            val halves = splitIntervalTokens(intervalPattern)
            if (halves != null) {
                val (first, second) = halves
                return formatPattern(first.withoutZoneFields(), locales.dateTime, startDate, startTime, locales.record) +
                    formatPattern(second.withoutZoneFields(), locales.dateTime, endDate, endTime, locales.record)
            }
        }

        // No entry for this field. Which of two things that means depends on
        // whether the field is coarser or finer than anything the skeleton names,
        // and the two want opposite answers.
        //
        // Coarser: the skeleton cannot show what they differ in, so the format
        // has to widen. Without this, a day-only skeleton over two months reads
        // "14 – 14", which is not a narrower answer than the right one, it is a
        // wrong one.
        val widened = widenedSkeleton(requested, difference)?.let(locales.matcher::bestPatternOrNull)
        if (widened != null) {
            val tokens = parseDateTimePattern(widened).withoutZoneFields()
            return substitute(
                record.fallback,
                formatPattern(tokens, locales.dateTime, startDate, startTime, locales.record),
                formatPattern(tokens, locales.dateTime, endDate, endTime, locales.record),
            )
        }

        // Finer: the two are the same value as far as this format can express, so
        // CLDR writes it once. "2026 – 2026" is never the answer to a year-shaped
        // question about two days in the same year.
        val first = formatOne(startDate, startTime)
        val second = formatOne(endDate, endTime)
        if (first == second) return first

        return substitute(record.fallback, first, second)
    }

    private fun recordFor(locale: Locale): IntervalRecord? {
        val key = locale.toLanguageTag()
        if (key in decoded) return decoded[key]
        val built = resolvedRecord(records, locale)?.let(::IntervalRecord)
        decoded[key] = built
        return built
    }

    public companion object
}

/**
 * One locale's interval patterns.
 *
 * Keyed by the canonical skeleton rather than by CLDR's own id, so that a
 * request resolved through the matcher lands on the same key whatever letters it
 * arrived with. The `j` skeleton is the reason: it resolves to `h` or `H`
 * depending on the locale, and only the resolved pattern knows which.
 */
@InternalKotlinxLocaleApi
public class IntervalRecord(record: String) {

    private val fields = record.split(FIELD_SEPARATOR)

    /**
     * The pattern for a pair no entry covers.
     *
     * The default is CLDR root's own `intervalFormatFallback` verbatim, thin
     * spaces included, rather than an approximation of it with ordinary spaces.
     * Codegen guarantees the field, so reaching the default means a record from
     * an older generator.
     */
    public val fallback: String = fields.getOrNull(0)?.takeIf(String::isNotEmpty) ?: "{0}\u2009\u2013\u2009{1}"

    private val byKey: Map<String, String> = buildMap {
        val entries = fields.getOrNull(1)?.takeIf(String::isNotEmpty)?.split(ENTRY_SEPARATOR).orEmpty()
        for (entry in entries) {
            val separator = entry.indexOf(KEY_SEPARATOR)
            if (separator <= 0) continue
            val key = entry.substring(0, separator)
            val pattern = entry.substring(separator + 1)
            val dot = key.lastIndexOf('.')
            if (dot <= 0) continue
            val canonical = SkeletonFields.of(key.substring(0, dot))?.canonicalString() ?: continue
            put(canonical + "." + key.substring(dot + 1), pattern)
        }
    }

    /**
     * The pattern for [canonicalSkeleton] differing in [difference].
     *
     * The hour is looked up as whichever of `H` or `h` [pattern] itself uses,
     * because CLDR keys the twelve- and twenty-four-hour forms separately and
     * only the resolved pattern says which one this locale reached for.
     */
    public fun patternFor(canonicalSkeleton: String, difference: Char, pattern: String): String? {
        if (difference != 'h') return byKey["$canonicalSkeleton.$difference"]
        val hourLetter = pattern.firstOrNull { it in "HhKk" } ?: 'H'
        return byKey["$canonicalSkeleton.$hourLetter"] ?: byKey["$canonicalSkeleton.H"] ?: byKey["$canonicalSkeleton.h"]
    }

    public companion object
}

/**
 * Splits an interval pattern into the half that formats the start and the half
 * that formats the end.
 *
 * The split point is the second occurrence of the first field that repeats, not
 * of the greatest-difference field, and the fields that count as the same are
 * the ones CLDR treats as one: `M` and `L`, `E` and `e` and `c`, the four hour
 * letters. A per-letter implementation gets `d MMM – d LLL` wrong.
 *
 * Null when nothing repeats, which CLDR does write: those patterns format once.
 */
internal fun splitIntervalTokens(pattern: String): Pair<List<PatternToken>, List<PatternToken>>? {
    val tokens = parseDateTimePattern(pattern)
    val seen = HashSet<Char>()
    for ((index, token) in tokens.withIndex()) {
        if (token !is PatternToken.Field) continue
        if (!seen.add(intervalFieldGroup(token.letter))) {
            return tokens.subList(0, index) to tokens.subList(index, tokens.size)
        }
    }
    return null
}

/** The letters CLDR treats as one field for the purpose of finding the repeat. */
private fun intervalFieldGroup(letter: Char): Char = when (letter) {
    'L' -> 'M'
    'e', 'c' -> 'E'
    'k', 'K', 'H' -> 'h'
    'u', 'U', 'r' -> 'y'
    'b', 'B' -> 'a'
    else -> letter
}
