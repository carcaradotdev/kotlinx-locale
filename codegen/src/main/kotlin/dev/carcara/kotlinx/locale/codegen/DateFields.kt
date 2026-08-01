package dev.carcara.kotlinx.locale.codegen

import java.io.File

/**
 * The eight units this library carries relative names for, in the order the
 * record encodes them.
 *
 * CLDR carries more. The seven per-weekday fields (`mon` through `sun`, which
 * spell "last Monday") are another quarter of a megabyte for a feature nobody
 * has asked for, and `era`, `weekOfMonth`, `dayOfYear`, `weekdayOfMonth`,
 * `dayperiod`, `zone` and `relativePeriod` are left out for the same reason.
 * Leaving them out is a decision rather than an omission.
 */
internal val RELATIVE_UNITS = listOf("year", "quarter", "month", "week", "day", "hour", "minute", "second")

/** The plural categories in the order the record stores them. */
private val CATEGORY_ORDER = listOf("zero", "one", "two", "few", "many", "other")

/** The three widths, as the suffix CLDR keys them by. */
private val WIDTHS = listOf("", "-short", "-narrow")

/** One unit's eighteen slots: the display name, five literals, six future and six past forms. */
private const val SLOTS_PER_UNIT = 18

/** One locale file's `dates/fields` subset, sparse the way every partial here is. */
class PartialRelativeTime {
    /** `"<unit><width>"` to its eighteen slots, with null for anything the file does not declare. */
    val units = LinkedHashMap<String, Array<String?>>()
}

fun parseRelativeTime(file: File): PartialRelativeTime {
    val partial = PartialRelativeTime()
    val fields = parseXml(file).documentElement.child("dates")?.child("fields") ?: return partial

    for (field in fields.childElements("field")) {
        val type = field.getAttribute("type")
        val unit = RELATIVE_UNITS.firstOrNull { type == it || type.startsWith("$it-") } ?: continue
        val width = type.removePrefix(unit)
        if (width !in WIDTHS) continue

        val slots = partial.units.getOrPut(type) { arrayOfNulls(SLOTS_PER_UNIT) }
        field.child("displayName")?.textContent?.cleaned()?.let { if (slots[0] == null) slots[0] = it }

        for (relative in field.childElements("relative")) {
            val offset = relative.getAttribute("type").toIntOrNull() ?: continue
            if (offset !in -2..2) continue
            val index = 1 + offset + 2
            relative.textContent.cleaned()?.let { if (slots[index] == null) slots[index] = it }
        }

        for (relativeTime in field.childElements("relativeTime")) {
            val base = when (relativeTime.getAttribute("type")) {
                "future" -> 6
                "past" -> 12
                else -> continue
            }
            for (pattern in relativeTime.childElements("relativeTimePattern")) {
                val category = CATEGORY_ORDER.indexOf(pattern.getAttribute("count"))
                if (category < 0) continue
                pattern.textContent.cleaned()?.let { if (slots[base + category] == null) slots[base + category] = it }
            }
        }
    }
    return partial
}

/** One locale's relative-time wording, resolved: three width blocks of eight units. */
class ResolvedRelativeTime(val blocks: List<List<List<String>>>)

/**
 * Resolves relative time for [id] across the inheritance chain, then applies the
 * lateral width fallback.
 *
 * The lateral step has to happen here rather than at runtime. A `↑↑↑` under
 * `day-short` means "the same as `day` in this locale", which is a sideways
 * reference the per-locale chain walk cannot express, so the widths are folded
 * flat and a width that ends up identical to the base is stored empty.
 */
fun Flattener.resolveRelativeTime(id: String, parse: (String) -> PartialRelativeTime): ResolvedRelativeTime {
    val chain = dataChain(id)
    val merged = LinkedHashMap<String, Array<String?>>()
    for (level in chain) {
        for ((key, slots) in parse(level).units) {
            val target = merged.getOrPut(key) { arrayOfNulls(SLOTS_PER_UNIT) }
            for (i in target.indices) if (target[i] == null) target[i] = slots[i]
        }
    }

    val base = RELATIVE_UNITS.map { unit -> merged[unit]?.map { it.orEmpty() } ?: List(SLOTS_PER_UNIT) { "" } }
    val blocks = ArrayList<List<List<String>>>(3)
    blocks += base
    for (width in listOf("-short", "-narrow")) {
        val block = RELATIVE_UNITS.mapIndexed { index, unit ->
            val slots = merged["$unit$width"]
            List(SLOTS_PER_UNIT) { slot -> slots?.get(slot) ?: base[index][slot] }
        }
        blocks += if (block == base) emptyList() else block
    }
    return ResolvedRelativeTime(blocks)
}

/** Three width blocks of eight units of eighteen slots, empty where a width matches the base. */
fun ResolvedRelativeTime.encode(): String = blocks.joinToString(FIELD_SEPARATOR) { block ->
    if (block.isEmpty()) {
        ""
    } else {
        block.joinToString(LIST_SEPARATOR) { unit -> unit.joinToString(KEY_SEPARATOR) }
    }
}
