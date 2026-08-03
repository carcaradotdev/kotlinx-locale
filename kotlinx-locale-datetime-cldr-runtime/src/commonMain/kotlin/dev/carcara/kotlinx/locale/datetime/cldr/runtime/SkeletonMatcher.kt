@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.datetime.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

/**
 * The letters a caller may ask for.
 *
 * Everything UTS #35 defines that this library can render, plus the three
 * request-only letters. A skeleton naming anything else — a time zone, a week
 * number, a fractional second, a non-gregorian cyclic year — is rejected rather
 * than quietly answered with a pattern that renders one field short.
 */
private const val REQUESTABLE_LETTERS = "GyuQqMLdDEecabBhHKkmsjJC"

/** ICU's stand-in when a locale declares no append format for a field. */
private const val DEFAULT_APPEND_FORMAT = "{0} ├{2}: {1}┤"

/** One entry in the pool the matcher scores a request against. */
internal class SkeletonCandidate(
    val fields: SkeletonFields,
    val pattern: String,
    /**
     * Whether this came from `availableFormats`, where CLDR declared the
     * skeleton itself rather than it being read back off a pattern. Only those
     * get to keep their own field widths against a request that asked for the
     * same width, which is what stops `MMMd` widening a locale's own `d MMM`.
     */
    val skeletonWasSpecified: Boolean,
)

/**
 * The skeleton matcher: UTS #35's "best pattern for a set of fields".
 *
 * One of these is built per locale, over that locale's `availableFormats` plus
 * its four standard date and four standard time patterns plus one bare pattern
 * per field. The last two matter more than they look: they are what lets a
 * request for a combination CLDR never wrote still be answered, and they
 * guarantee the append loop always terminates because every field has something
 * that covers it.
 *
 * Written from scratch against the specification, with ICU's
 * `DateTimePatternGenerator` read for the corners the specification states
 * tersely. Nothing here delegates to ICU at runtime; the agreement between the
 * two is a test, not a dependency.
 */
internal class SkeletonMatcher(private val record: SkeletonRecord, private val dateTime: DateTimeRecord) {

    /**
     * The pool, ordered the way ICU keys its candidate map.
     *
     * The order is only visible when a request ties with two candidates on both
     * distance and which fields are missing, which is rare — but reproducing it
     * is what makes those ties resolve the same way.
     */
    private val candidates: List<SkeletonCandidate> = buildPool().sortedWith { a, b -> b.fields.compareTo(a.fields) }

    /**
     * Builds the pool in ICU's order, with its two rejection rules.
     *
     * A bare field pattern and a standard pattern are both rejected when
     * something already covers the same base skeleton — which is how a locale's
     * LONG time pattern drops out when its FULL one differs only in zone width.
     * A CLDR entry displaces either of those, but never another CLDR entry, so
     * the nearest declaration in the inheritance chain is the one that stands.
     */
    private fun buildPool(): List<SkeletonCandidate> {
        val byIdentity = LinkedHashMap<String, SkeletonCandidate>()
        val byBase = HashMap<String, SkeletonCandidate>()

        fun add(source: String, pattern: String, declaredSkeleton: Boolean) {
            val fields = SkeletonFields.of(source) ?: return
            val base = fields.basePattern()
            if (!declaredSkeleton && byBase.containsKey(base)) return
            val identity = fields.identity()
            val previous = byIdentity[identity]
            if (previous != null && (!declaredSkeleton || previous.skeletonWasSpecified)) return
            val candidate = SkeletonCandidate(fields, pattern, declaredSkeleton)
            byIdentity[identity] = candidate
            byBase[base] = candidate
        }

        for (letter in CANONICAL_FIELD_PATTERNS) add(letter, letter, declaredSkeleton = false)
        for (pattern in dateTime.dateFormats) add(pattern, pattern, declaredSkeleton = false)
        for (pattern in dateTime.timeFormats) add(pattern, pattern, declaredSkeleton = false)
        for ((id, pattern) in record.availableFormats) add(id, pattern, declaredSkeleton = true)

        return byIdentity.values.toList()
    }

    /**
     * The locale's pattern for [skeleton], or `null` when the skeleton names a
     * field this library cannot render.
     */
    fun bestPatternOrNull(skeleton: String): String? {
        val mapped = resolveHourMetacharacters(skeleton)
        if (mapped.skeleton.any { it.isAsciiLetter() && it !in REQUESTABLE_LETTERS }) return null
        val request = SkeletonFields.of(mapped.skeleton) ?: return null

        val difference = FieldDifference()
        val best = bestRaw(request, includeMask = -1, into = difference) ?: return null
        // An exact cover is returned as it stands: no gluing, no appending.
        if (difference.missing == 0 && difference.extra == 0) {
            return adjustFieldTypes(best, request, mapped.usesCapitalJ)
        }

        // Otherwise the request is answered in two halves and joined with the
        // locale's own date-time glue, which is what keeps "3:05 PM" from
        // landing in the middle of a date.
        val needed = request.fieldMask()
        val datePart = bestAppending(request, needed and SkeletonField.DATE_MASK, mapped.usesCapitalJ)
        val timePart = bestAppending(request, needed and SkeletonField.TIME_MASK, mapped.usesCapitalJ)
        if (datePart == null) return timePart
        if (timePart == null) return datePart
        val glue = record.glueAtTimeFormats[glueStyleFor(request.canonicalString())]
        return substitute(glue, timePart, datePart)
    }

    /** The closest candidate to [request], counting only the fields in [includeMask]. */
    private fun bestRaw(request: SkeletonFields, includeMask: Int, into: FieldDifference): SkeletonCandidate? {
        var bestDistance = Int.MAX_VALUE
        var bestMissing = Int.MIN_VALUE
        var best: SkeletonCandidate? = null
        val current = FieldDifference()
        for (candidate in candidates) {
            val distance = request.distanceTo(candidate.fields, includeMask, current)
            if (distance < bestDistance || (distance == bestDistance && bestMissing < current.missing)) {
                bestDistance = distance
                bestMissing = current.missing
                best = candidate
                into.copyFrom(current)
                if (distance == 0) break
            }
        }
        return best
    }

    /**
     * One half of the request, with anything the winner did not cover folded in
     * through the locale's append formats.
     *
     * Each round asks the pool to cover what is still missing, so a single round
     * can supply several fields at once; the append format used is the one for
     * the highest-numbered field it supplied.
     */
    private fun bestAppending(request: SkeletonFields, missingFields: Int, usesCapitalJ: Boolean): String? {
        if (missingFields == 0) return null
        val difference = FieldDifference()
        val first = bestRaw(request, missingFields, difference) ?: return null
        var result = adjustFieldTypes(first, request, usesCapitalJ)

        while (difference.missing != 0) {
            val stillMissing = difference.missing
            val next = bestRaw(request, stillMissing, difference) ?: break
            val addition = adjustFieldTypes(next, request, usesCapitalJ)
            val covered = stillMissing and difference.missing.inv()
            // Every field has a bare pattern in the pool, so a round that covers
            // nothing cannot happen; bail rather than spin if one ever does.
            if (covered == 0) break
            val field = topFieldOf(covered)
            val format = record.appendFormat(field).ifEmpty { DEFAULT_APPEND_FORMAT }
            result = substitute(format, result, addition, "'" + record.fieldName(field).ifEmpty { "F$field" } + "'")
        }
        return result
    }

    /**
     * Rewrites the winning pattern's field widths and letters from the request.
     *
     * Widths come from the request, with three exceptions that all say the same
     * thing: the locale knows better. Hour, minute and second keep the width the
     * locale wrote, so asking for `hhmm` in `en` still gives `h:mm a`. A CLDR
     * entry keeps its own width where the request agrees with the id it was
     * declared under, or where one of the two is numeric and the other is text.
     * And month, weekday, hour and year take their *letter* from the pattern, so
     * whether a locale writes a stand-alone `L` or a formatting `M` is the
     * locale's business rather than the caller's.
     */
    private fun adjustFieldTypes(candidate: SkeletonCandidate, request: SkeletonFields, usesCapitalJ: Boolean): String {
        val out = StringBuilder()
        for (token in parseDateTimePattern(candidate.pattern)) {
            when (token) {
                is PatternToken.Literal -> out.append(quoteLiteral(token.text))
                is PatternToken.Field -> {
                    val info = fieldInfoFor(token.letter, token.count)
                    if (info == null) {
                        repeat(token.count) { out.append(token.letter) }
                        continue
                    }
                    val field = info.field
                    if (request.weights[field] == 0) {
                        // The request never asked for this field; leave it alone.
                        repeat(token.count) { out.append(token.letter) }
                        continue
                    }

                    val requestedChar = request.chars[field]
                    // E, EE and EEE are all the abbreviated weekday.
                    val requestedLength = if (requestedChar == 'E' && request.lengths[field] < 3) {
                        3
                    } else {
                        request.lengths[field]
                    }

                    val keepPatternWidth = field == SkeletonField.HOUR ||
                        field == SkeletonField.MINUTE ||
                        field == SkeletonField.SECOND ||
                        (
                            candidate.skeletonWasSpecified &&
                                requestedChar != 'c' &&
                                requestedChar != 'e' &&
                                (
                                    candidate.fields.lengths[field] == requestedLength ||
                                        info.isNumeric != request.isNumeric(field)
                                    )
                            )
                    val length = if (keepPatternWidth) token.count else requestedLength

                    val fromPattern = field == SkeletonField.HOUR ||
                        field == SkeletonField.MONTH ||
                        field == SkeletonField.WEEKDAY ||
                        (field == SkeletonField.YEAR && requestedChar != 'Y')
                    var letter = if (fromPattern) token.letter else requestedChar
                    // E has no numeric form; below three it has to be e.
                    if (letter == 'E' && length < 3) letter = 'e'
                    if (field == SkeletonField.HOUR) letter = hourLetterFor(requestedChar, letter, usesCapitalJ)

                    repeat(length) { out.append(letter) }
                }
            }
        }
        return out.toString()
    }

    /**
     * Which of `h H k K` writes the hour.
     *
     * `J` and a request that already matches take the locale's preference. A
     * request that differs only in whether midnight is 0 or 12 is nudged onto
     * the locale's side of that; a request that deliberately crosses between the
     * twelve- and twenty-four-hour families is left alone.
     */
    private fun hourLetterFor(requestedChar: Char, patternChar: Char, usesCapitalJ: Boolean): Char {
        val preferred = record.preferredHourChar
        return when {
            usesCapitalJ || requestedChar == preferred -> preferred
            requestedChar == 'h' && preferred == 'K' -> 'K'
            requestedChar == 'H' && preferred == 'k' -> 'k'
            requestedChar == 'k' && preferred == 'H' -> 'H'
            requestedChar == 'K' && preferred == 'h' -> 'h'
            else -> patternChar
        }
    }

    /**
     * Replaces `j`, `J` and `C` with the hour and day period letters they stand
     * for in this locale.
     *
     * `j` asks for the locale's preferred hour and the day period that goes with
     * it; `J` asks for the hour with no day period at all; `C` asks for the first
     * *allowed* format, whose trailing `b` or `B` can call for a flexible day
     * period where `j` would have asked for AM/PM. A run of the letter encodes
     * both widths at once: its parity picks the hour width and its length the
     * day period's.
     */
    private fun resolveHourMetacharacters(skeleton: String): MappedSkeleton {
        if (skeleton.none { it == 'j' || it == 'J' || it == 'C' || it == '\'' }) {
            return MappedSkeleton(skeleton, usesCapitalJ = false)
        }
        val out = StringBuilder(skeleton.length + 4)
        var usesCapitalJ = false
        var quoted = false
        var i = 0
        while (i < skeleton.length) {
            val ch = skeleton[i]
            when {
                ch == '\'' -> quoted = !quoted
                quoted -> {} // quoted text names no field, so it is dropped
                ch == 'j' || ch == 'C' -> {
                    var extra = 0
                    while (i + 1 < skeleton.length && skeleton[i + 1] == ch) {
                        extra++
                        i++
                    }
                    var hourChar = 'h'
                    var dayPeriodChar = 'a'
                    if (ch == 'j') {
                        hourChar = record.preferredHourChar
                    } else {
                        val allowed = record.firstAllowedHourFormat
                        hourChar = allowed[0]
                        val last = allowed[allowed.length - 1]
                        if (last == 'b' || last == 'B') dayPeriodChar = last
                    }
                    val hourLength = 1 + (extra and 1)
                    var dayPeriodLength = if (extra < 2) 1 else 3 + (extra shr 1)
                    if (hourChar == 'H' || hourChar == 'k') dayPeriodLength = 0
                    repeat(dayPeriodLength) { out.append(dayPeriodChar) }
                    repeat(hourLength) { out.append(hourChar) }
                }
                ch == 'J' -> {
                    out.append('H')
                    usesCapitalJ = true
                }
                else -> out.append(ch)
            }
            i++
        }
        return MappedSkeleton(out.toString(), usesCapitalJ)
    }
}

private class MappedSkeleton(val skeleton: String, val usesCapitalJ: Boolean)

/** One bare pattern per field, so that every field has something that covers it. */
private val CANONICAL_FIELD_PATTERNS = listOf(
    "G", "y", "Q", "M", "w", "W", "E",
    "d", "D", "F", "a",
    "H", "m", "s", "S", "v",
)

/**
 * Which of the four date-time glue patterns joins the two halves.
 *
 * CLDR picks it off how wide the month is: a wide month with a weekday reads as
 * a full date and takes the wordiest glue, and a numeric month takes the
 * tersest.
 */
private fun glueStyleFor(canonical: String): Int {
    val first = canonical.indexOf('M')
    val monthLength = if (first < 0) 0 else canonical.lastIndexOf('M') - first + 1
    return when {
        monthLength == 4 -> if (canonical.contains('E')) 0 else 1
        monthLength == 3 -> 2
        else -> 3
    }
}

/**
 * Fills `{0}`, `{1}` and `{2}` in one pass.
 *
 * One pass rather than three replacements, so that an argument which happens to
 * contain a placeholder is not read back as one. An apostrophe is only an escape
 * before a brace, which is what leaves a glue pattern's `'at'` quoted in the
 * result — where it has to stay, because the result is a pattern rather than
 * finished text.
 */
internal fun substitute(template: String, vararg arguments: String): String = buildString(template.length + 16) {
    var i = 0
    while (i < template.length) {
        val ch = template[i]
        when {
            ch == '\'' && i + 1 < template.length && (template[i + 1] == '{' || template[i + 1] == '}') -> {
                append(template[i + 1])
                i += 2
            }
            ch == '{' && i + 2 < template.length && template[i + 2] == '}' -> {
                val index = template[i + 1] - '0'
                if (index in arguments.indices) append(arguments[index]) else append(template, i, i + 3)
                i += 3
            }
            else -> {
                append(ch)
                i++
            }
        }
    }
}

/**
 * Re-quotes a literal so it survives being read back as a pattern.
 *
 * Only the runs that need it are quoted, which is what keeps the output looking
 * like what CLDR wrote: Portuguese `d 'de' MMM 'de' y` comes back with its two
 * quoted words and its unquoted spaces. Latin and Cyrillic letters are quoted
 * even outside ASCII, because those are the scripts whose letters a pattern
 * reader could mistake for fields; CJK and Arabic are left bare, the way
 * `y年M月d日` is written.
 */
internal fun quoteLiteral(text: String): String {
    if (text.none(Char::needsQuoting)) return text.replace("'", "''")
    return buildString(text.length + 4) {
        var quoting = false
        for (ch in text) {
            // A doubled apostrophe reads as one apostrophe inside a quoted run
            // and outside it alike, so it never has to close the run.
            if (ch == '\'') {
                append("''")
                continue
            }
            if (ch.needsQuoting() != quoting) {
                append('\'')
                quoting = !quoting
            }
            append(ch)
        }
        if (quoting) append('\'')
    }
}

/** Whether a literal character would otherwise read as a pattern field. */
private fun Char.needsQuoting(): Boolean = isAsciiLetter() ||
    // Latin-1 Supplement through IPA Extensions, which are all Latin-script
    // letters, minus the two mathematical symbols sitting among them. Ewe's
    // 'aɖabaƒoƒo' needs the top of that range. Combining marks and modifier
    // letters are deliberately absent: they are inherited and common script
    // rather than Latin, so a pattern reader cannot mistake them for fields
    // and CLDR leaves them outside the quotes.
    (this in '\u00C0'..'\u02AF' && this != '\u00D7' && this != '\u00F7') ||
    this in '\u0400'..'\u052F' ||
    // Cyrillic and its supplement
    this in '\u1E00'..'\u1EFF' // Latin Extended Additional

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
