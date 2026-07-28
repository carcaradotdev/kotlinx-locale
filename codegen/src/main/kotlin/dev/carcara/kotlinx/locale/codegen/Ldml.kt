package dev.carcara.kotlinx.locale.codegen

import java.io.File
import org.w3c.dom.Element

/** CLDR marks "inherit from parent" with three up arrows. */
private const val INHERITANCE_MARKER = "↑↑↑"

private val STYLES = listOf("full", "long", "medium", "short")
private val DAY_KEYS_ISO = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

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
    var numberingSystem: String? = null
}

fun parseLdml(file: File): PartialLocaleData {
    val data = PartialLocaleData()
    val ldml = parseXml(file).documentElement

    ldml.path("numbers", "defaultNumberingSystem")
        ?.takeIf { !it.hasAttribute("alt") }
        ?.textContent?.cleaned()?.let { data.numberingSystem = it }

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

    fun readLengths(container: Element?, lengthTag: String, formatTag: String, target: Array<String?>) {
        container ?: return
        for ((index, style) in STYLES.withIndex()) {
            val lengthEl = container.child(lengthTag, "type" to style) ?: continue
            // CLDR 44+ can carry several dateTimeFormat elements (standard/atTime);
            // use the standard one, which is what ICU's style-based formatting uses.
            val formatEl = lengthEl.childElements(formatTag).firstOrNull {
                val type = it.getAttribute("type")
                type.isEmpty() || type == "standard"
            } ?: continue
            val pattern = formatEl.child("pattern")?.takeIf { !it.hasAttribute("alt") } ?: continue
            if (target[index] == null) target[index] = pattern.textContent.cleaned()
        }
    }

    readLengths(gregorian.child("dateFormats"), "dateFormatLength", "dateFormat", data.dateFormats)
    readLengths(gregorian.child("timeFormats"), "timeFormatLength", "timeFormat", data.timeFormats)
    readLengths(gregorian.child("dateTimeFormats"), "dateTimeFormatLength", "dateTimeFormat", data.glueFormats)

    return data
}

private fun String.cleaned(): String? = takeUnless { it == INHERITANCE_MARKER }
