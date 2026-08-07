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

package dev.carcara.kotlinx.locale.codegen

import org.w3c.dom.Element
import java.io.File

/** CLDR marks "inherit from parent" with three up arrows. */
internal const val INHERITANCE_MARKER = "↑↑↑"

private val STYLES = listOf("full", "long", "medium", "short")
private val DAY_KEYS_ISO = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

/**
 * The UTS #35 date field types, in the order the skeleton records encode
 * appendItem patterns and field display names in. `SkeletonFields` in
 * `-datetime-cldr-runtime` mirrors it; the two are a convention, the way
 * [DAY_PERIOD_TYPES] and `DayPeriodCodes` already are.
 */
internal val DATE_FIELD_TYPES = listOf(
    "era", "year", "quarter", "month", "week", "weekOfMonth", "weekday",
    "day", "dayOfYear", "weekdayOfMonth", "dayperiod",
    "hour", "minute", "second", "fractionalSecond", "zone",
)

/**
 * The fields a skeleton can both ask for and this library can render.
 *
 * The other six carry no append format or display name into the emitted tables.
 * A field the matcher will never be handed cannot end up being appended, and the
 * display names are the larger half of that table — "day of year" and "weekday
 * of the month" are among the longest strings in it.
 */
/**
 * The `durationUnit` types, in the order the encoded record carries them.
 *
 * CLDR names exactly these three and offers no way to ask for anything else: an
 * hour and a minute, all three, or a minute and a second.
 */
internal val DURATION_UNIT_TYPES = listOf("hm", "hms", "ms")

internal val RENDERABLE_FIELDS = setOf(
    "era", "year", "quarter", "month", "weekday", "day", "dayperiod", "hour", "minute", "second",
)

/**
 * The `request` attribute CLDR spells each field with under `appendItems`,
 * positionally against [DATE_FIELD_TYPES]. Empty where CLDR declares no append
 * format for that field, which is every field a skeleton cannot ask for on its
 * own.
 */
private val APPEND_ITEM_REQUESTS = listOf(
    "Era", "Year", "Quarter", "Month", "Week", "", "Day-Of-Week",
    "Day", "", "", "",
    "Hour", "Minute", "Second", "", "Timezone",
)

/**
 * The gregorian-calendar subset of one LDML file. Every field is nullable: a locale
 * file only carries what differs from its parent; [flatten] merges the chain.
 */
class PartialLocaleData {
    val monthsWide = arrayOfNulls<String>(12)
    val monthsAbbr = arrayOfNulls<String>(12)
    val monthsNarrow = arrayOfNulls<String>(12)

    /**
     * The stand-alone forms: what a calendar header or a month picker wants,
     * where the format forms are what goes inside a date.
     *
     * In many languages the two differ by grammatical case — Czech July is
     * `července` in a date and `červenec` alone — and it is not only case:
     * Croatian writes its stand-alone narrow months as `7.`, a number.
     *
     * Narrow is also what emulates root.xml's alias, which runs the other way:
     * format narrow inherits from stand-alone narrow rather than the reverse.
     */
    val monthsStandaloneWide = arrayOfNulls<String>(12)
    val monthsStandaloneAbbr = arrayOfNulls<String>(12)
    val monthsStandaloneNarrow = arrayOfNulls<String>(12)
    val daysWide = arrayOfNulls<String>(7)
    val daysAbbr = arrayOfNulls<String>(7)
    val daysNarrow = arrayOfNulls<String>(7)
    val daysStandaloneWide = arrayOfNulls<String>(7)
    val daysStandaloneAbbr = arrayOfNulls<String>(7)
    val daysStandaloneNarrow = arrayOfNulls<String>(7)
    var am: String? = null
    var pm: String? = null

    /** Flexible day period names, indexed like [DAY_PERIOD_TYPES] minus am/pm. */
    val dayPeriods = arrayOfNulls<String>(DAY_PERIOD_TYPES.size - 2)
    var era0: String? = null
    var era1: String? = null
    val dateFormats = arrayOfNulls<String>(4)
    val timeFormats = arrayOfNulls<String>(4)
    val glueFormats = arrayOfNulls<String>(4)

    /**
     * The `atTime` date-time glue, which is what skeleton formatting joins its
     * two halves with — `{1} 'at' {0}` where the standard glue is `{1}, {0}`.
     */
    val glueAtTimeFormats = arrayOfNulls<String>(4)
    var numberingSystem: String? = null

    /** `durationUnit` patterns, indexed by [DURATION_UNIT_TYPES]. */
    val durationPatterns = arrayOfNulls<String>(DURATION_UNIT_TYPES.size)

    /** Gregorian `availableFormats`: skeleton id to pattern. */
    val availableFormats = LinkedHashMap<String, String>()

    /** `appendItems` patterns, indexed by [DATE_FIELD_TYPES]. */
    val appendItems = arrayOfNulls<String>(DATE_FIELD_TYPES.size)

    /** `{0} – {1}`, the pattern for an interval no specific entry covers. */
    var intervalFallback: String? = null

    /**
     * Gregorian `intervalFormats`: skeleton id to greatest-difference field to
     * pattern.
     *
     * Two levels rather than one flat key, because inheritance works per
     * greatest difference. A locale declaring `yMd` with only a `d` entry still
     * takes `y` and `M` from its parent.
     */
    val intervalFormats = LinkedHashMap<String, LinkedHashMap<String, String>>()

    /** Field display names, indexed by [DATE_FIELD_TYPES]; the `{2}` an appendItem writes. */
    val fieldNames = arrayOfNulls<String>(DATE_FIELD_TYPES.size)
    val quartersWide = arrayOfNulls<String>(4)
    val quartersAbbr = arrayOfNulls<String>(4)
    val quartersStandaloneWide = arrayOfNulls<String>(4)
    val quartersStandaloneAbbr = arrayOfNulls<String>(4)
}

fun parseLdml(file: File): PartialLocaleData {
    val data = PartialLocaleData()
    val ldml = parseXml(file).documentElement
    checkNoContainerDrafts(ldml, file.name)

    ldml.path("numbers", "defaultNumberingSystem")
        ?.takeIf { !it.hasAttribute("alt") }
        ?.textContent?.cleaned()?.let { data.numberingSystem = it }

    // Field display names sit beside the calendars rather than inside one: they
    // name the field itself ("month", "Monat"), not anything calendar-specific.
    ldml.child("dates")?.child("fields")?.let { fields ->
        for (field in fields.childElements("field")) {
            val index = DATE_FIELD_TYPES.indexOf(field.getAttribute("type"))
            if (index < 0 || data.fieldNames[index] != null) continue
            val displayName = field.child("displayName")?.takeIf { !it.hasAttribute("alt") } ?: continue
            data.fieldNames[index] = displayName.textContent.cleaned()
        }
    }

    // Duration patterns sit under `units` rather than under a calendar, because
    // `m:ss` is an elapsed quantity rather than a time of day. Read before the
    // early return below, since a locale can carry these and no gregorian block.
    ldml.child("units")?.let { units ->
        for (unit in units.childElements("durationUnit")) {
            val index = DURATION_UNIT_TYPES.indexOf(unit.getAttribute("type"))
            if (index < 0 || data.durationPatterns[index] != null) continue
            val pattern = unit.childElements("durationUnitPattern").firstOrNull { !it.hasAttribute("alt") } ?: continue
            data.durationPatterns[index] = pattern.textContent.cleaned()
        }
    }

    val gregorian = ldml.child("dates")
        ?.child("calendars")
        ?.child("calendar", "type" to "gregorian")
        ?: return data

    gregorian.child("months")?.let { months ->
        for ((context, width, target) in listOf(
            Triple("format", "wide", data.monthsWide),
            Triple("format", "abbreviated", data.monthsAbbr),
            Triple("format", "narrow", data.monthsNarrow),
            Triple("stand-alone", "wide", data.monthsStandaloneWide),
            Triple("stand-alone", "abbreviated", data.monthsStandaloneAbbr),
            Triple("stand-alone", "narrow", data.monthsStandaloneNarrow),
        )) {
            val widthEl = months.child("monthContext", "type" to context)
                ?.child("monthWidth", "type" to width) ?: continue
            for (month in widthEl.childElements("month")) {
                if (month.hasAttribute("alt")) continue
                val index = month.getAttribute("type").toIntOrNull()?.minus(1) ?: continue
                if (index in 0..11 && target[index] == null) target[index] = month.textContent.cleaned()
            }
        }
    }

    gregorian.child("quarters")?.let { quarters ->
        for ((context, width, target) in listOf(
            Triple("format", "wide", data.quartersWide),
            Triple("format", "abbreviated", data.quartersAbbr),
            Triple("stand-alone", "wide", data.quartersStandaloneWide),
            Triple("stand-alone", "abbreviated", data.quartersStandaloneAbbr),
        )) {
            val widthEl = quarters.child("quarterContext", "type" to context)
                ?.child("quarterWidth", "type" to width) ?: continue
            for (quarter in widthEl.childElements("quarter")) {
                if (quarter.hasAttribute("alt")) continue
                val index = quarter.getAttribute("type").toIntOrNull()?.minus(1) ?: continue
                if (index in 0..3 && target[index] == null) target[index] = quarter.textContent.cleaned()
            }
        }
    }

    gregorian.child("days")?.let { days ->
        for ((context, width, target) in listOf(
            Triple("format", "wide", data.daysWide),
            Triple("format", "abbreviated", data.daysAbbr),
            Triple("format", "narrow", data.daysNarrow),
            Triple("stand-alone", "wide", data.daysStandaloneWide),
            Triple("stand-alone", "abbreviated", data.daysStandaloneAbbr),
            Triple("stand-alone", "narrow", data.daysStandaloneNarrow),
        )) {
            val widthEl = days.child("dayContext", "type" to context)
                ?.child("dayWidth", "type" to width) ?: continue
            for (day in widthEl.childElements("day")) {
                if (day.hasAttribute("alt")) continue
                val index = DAY_KEYS_ISO.indexOf(day.getAttribute("type"))
                if (index >= 0 && target[index] == null) target[index] = day.textContent.cleaned()
            }
        }
    }

    // Abbreviated is the base width for day periods (root aliases wide and
    // narrow to it), and it is what the a/b/B pattern fields render.
    gregorian.child("dayPeriods")
        ?.child("dayPeriodContext", "type" to "format")
        ?.child("dayPeriodWidth", "type" to "abbreviated")
        ?.let { widthEl ->
            for (period in widthEl.childElements("dayPeriod")) {
                if (period.hasAttribute("alt")) continue
                when (val type = period.getAttribute("type")) {
                    "am" -> if (data.am == null) data.am = period.textContent.cleaned()
                    "pm" -> if (data.pm == null) data.pm = period.textContent.cleaned()
                    else -> {
                        val index = DAY_PERIOD_TYPES.indexOf(type) - 2
                        if (index >= 0 && data.dayPeriods[index] == null) {
                            data.dayPeriods[index] = period.textContent.cleaned()
                        }
                    }
                }
            }
        }

    gregorian.child("eras")?.child("eraAbbr")?.let { eras ->
        for (era in eras.childElements("era")) {
            if (era.hasAttribute("alt")) continue
            when (era.getAttribute("type")) {
                "0" -> if (data.era0 == null) data.era0 = era.textContent.cleaned()
                "1" -> if (data.era1 == null) data.era1 = era.textContent.cleaned()
            }
        }
    }

    // CLDR 44+ can carry several dateTimeFormat elements. The standard one is
    // what style-based formatting uses; the atTime one is what a skeleton
    // spanning a date and a time is joined with.
    fun readLengths(container: Element?, lengthTag: String, formatTag: String, target: Array<String?>, wantedType: String = "standard") {
        container ?: return
        for ((index, style) in STYLES.withIndex()) {
            val lengthEl = container.child(lengthTag, "type" to style) ?: continue
            val formatEl = lengthEl.childElements(formatTag).firstOrNull {
                val type = it.getAttribute("type")
                (wantedType == "standard" && type.isEmpty()) || type == wantedType
            } ?: continue
            val pattern = formatEl.child("pattern")?.takeIf { !it.hasAttribute("alt") } ?: continue
            if (target[index] == null) target[index] = pattern.textContent.cleaned()
        }
    }

    readLengths(gregorian.child("dateFormats"), "dateFormatLength", "dateFormat", data.dateFormats)
    readLengths(gregorian.child("timeFormats"), "timeFormatLength", "timeFormat", data.timeFormats)
    readLengths(gregorian.child("dateTimeFormats"), "dateTimeFormatLength", "dateTimeFormat", data.glueFormats)
    readLengths(
        gregorian.child("dateTimeFormats"),
        "dateTimeFormatLength",
        "dateTimeFormat",
        data.glueAtTimeFormats,
        wantedType = "atTime",
    )

    gregorian.child("dateTimeFormats")?.let { dateTimeFormats ->
        dateTimeFormats.child("availableFormats")?.let { available ->
            for (item in available.childElements("dateFormatItem")) {
                // The alt="ascii" duplicates swap U+202F for a plain space; the
                // unmarked entry is the one CLDR means.
                if (item.hasAttribute("alt")) continue
                val id = item.getAttribute("id").takeIf(String::isNotEmpty) ?: continue
                val pattern = item.textContent.cleaned() ?: continue
                data.availableFormats.putIfAbsent(id, pattern)
            }
        }
        dateTimeFormats.child("appendItems")?.let { appendItems ->
            for (item in appendItems.childElements("appendItem")) {
                if (item.hasAttribute("alt")) continue
                // An absent request would read as the empty string, which is how
                // APPEND_ITEM_REQUESTS spells "CLDR declares none for this field".
                val request = item.getAttribute("request").takeIf(String::isNotEmpty) ?: continue
                val index = APPEND_ITEM_REQUESTS.indexOf(request)
                if (index < 0 || data.appendItems[index] != null) continue
                data.appendItems[index] = item.textContent.cleaned()
            }
        }
        dateTimeFormats.child("intervalFormats")?.let { intervalFormats ->
            if (data.intervalFallback == null) {
                data.intervalFallback = intervalFormats.child("intervalFormatFallback")
                    ?.takeIf { !it.hasAttribute("alt") }
                    ?.textContent
                    ?.cleaned()
            }
            for (item in intervalFormats.childElements("intervalFormatItem")) {
                if (item.hasAttribute("alt")) continue
                val id = item.getAttribute("id").takeIf(String::isNotEmpty) ?: continue
                val byDifference = data.intervalFormats.getOrPut(id) { LinkedHashMap() }
                for (difference in item.childElements("greatestDifference")) {
                    if (difference.hasAttribute("alt")) continue
                    val field = difference.getAttribute("id").takeIf(String::isNotEmpty) ?: continue
                    val pattern = difference.textContent.cleaned() ?: continue
                    byDifference.putIfAbsent(field, pattern)
                }
            }
        }
    }

    return data
}

internal fun String.cleaned(): String? = takeUnless { it == INHERITANCE_MARKER }
