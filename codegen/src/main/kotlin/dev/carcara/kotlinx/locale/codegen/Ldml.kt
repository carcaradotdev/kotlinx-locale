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

    /** Only used to emulate root.xml's narrow -> stand-alone narrow alias. */
    val monthsStandaloneNarrow = arrayOfNulls<String>(12)
    val daysWide = arrayOfNulls<String>(7)
    val daysAbbr = arrayOfNulls<String>(7)
    val daysNarrow = arrayOfNulls<String>(7)
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

    /** Gregorian `availableFormats`: skeleton id to pattern. */
    val availableFormats = LinkedHashMap<String, String>()

    /** `appendItems` patterns, indexed by [DATE_FIELD_TYPES]. */
    val appendItems = arrayOfNulls<String>(DATE_FIELD_TYPES.size)

    /** Field display names, indexed by [DATE_FIELD_TYPES]; the `{2}` an appendItem writes. */
    val fieldNames = arrayOfNulls<String>(DATE_FIELD_TYPES.size)
    val quartersWide = arrayOfNulls<String>(4)
    val quartersAbbr = arrayOfNulls<String>(4)
}

fun parseLdml(file: File): PartialLocaleData {
    val data = PartialLocaleData()
    val ldml = parseXml(file).documentElement

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

    val gregorian = ldml.child("dates")
        ?.child("calendars")
        ?.child("calendar", "type" to "gregorian")
        ?: return data

    gregorian.child("months")?.let { months ->
        for ((context, width, target) in listOf(
            Triple("format", "wide", data.monthsWide),
            Triple("format", "abbreviated", data.monthsAbbr),
            Triple("format", "narrow", data.monthsNarrow),
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
        for ((width, target) in listOf("wide" to data.quartersWide, "abbreviated" to data.quartersAbbr)) {
            val widthEl = quarters.child("quarterContext", "type" to "format")
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
    }

    return data
}

internal fun String.cleaned(): String? = takeUnless { it == INHERITANCE_MARKER }
