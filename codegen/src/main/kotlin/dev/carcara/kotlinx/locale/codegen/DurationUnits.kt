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

import java.io.File

/**
 * The fourteen time units this library carries measurement wording for, in the
 * order the record encodes them.
 *
 * Has to match `DurationUnit` in `kotlinx-locale-datetime-cldr-runtime`, which
 * is positional over this list.
 *
 * CLDR carries two more under `duration-`. `duration-fortnight` reaches twelve
 * locales in release-48-2 and `duration-day-person`, the form some languages use
 * when counting a person's days rather than days in the abstract, reaches
 * sixteen. Each would be a fifteenth of the table for wording almost nobody
 * could read. Leaving them out is a decision rather than an omission, the same
 * one [RELATIVE_UNITS] makes about the per-weekday fields.
 */
internal val DURATION_UNITS = listOf(
    "duration-century",
    "duration-decade",
    "duration-year",
    "duration-quarter",
    "duration-month",
    "duration-week",
    "duration-day",
    "duration-night",
    "duration-hour",
    "duration-minute",
    "duration-second",
    "duration-millisecond",
    "duration-microsecond",
    "duration-nanosecond",
)

/** The plural categories in the order the record stores them. */
private val CATEGORY_ORDER = listOf("zero", "one", "two", "few", "many", "other")

/** The three widths, in the order the record blocks them, as CLDR keys them. */
private val WIDTHS = listOf("long", "short", "narrow")

/**
 * Short first, then long, then narrow: the order the widths are consulted once
 * the asked-for one has nothing.
 *
 * Short leads because it is the most complete block CLDR has. Root declares only
 * a short one, so it is the only width every locale reaches something through.
 */
private val WIDTH_PRIORITY = listOf(1, 0, 2)

/**
 * Where width [width] looks for a pattern, in order: a width index, and whether
 * that step reads the asked-for plural category or `other`.
 *
 * The rule is the asked-for width's own category first, then each width in
 * [WIDTH_PRIORITY] contributing its own category and then its `other`, skipping
 * whatever has already been tried. Two consequences are worth naming because
 * either one looks like a bug from the other's side. French writes `1 an` out of
 * its short year rather than `1 ans` out of its own long `other`, because the
 * category is tried in short before `other` is accepted in long. German writes
 * `1 Jh.` out of its short century rather than `1 Jahrhundert` out of the long
 * one, because short's `other` is reached before long's category.
 *
 * Fitted to ICU rather than derived from UTS #35, which does not state it. Of the
 * 720 orders per width, 6 reproduce every long cell, 24 every short and 10 every
 * narrow; this is the one description that lands inside all three sets. It is
 * pinned by `IcuDurationUnitGoldenData` over three widths, eight values and
 * thirty locales, so changing it fails the build rather than the output.
 */
private fun patternFallback(width: Int): List<Pair<Int, Boolean>> {
    val steps = LinkedHashSet<Pair<Int, Boolean>>()
    steps += width to false
    for (level in WIDTH_PRIORITY) {
        steps += level to false
        steps += level to true
    }
    return steps.toList()
}

private val PATTERN_FALLBACK: List<List<Pair<Int, Boolean>>> = WIDTHS.indices.map(::patternFallback)

/** Where each width looks for a display name, which has no plural forms. */
private val NAME_FALLBACK: List<List<Int>> = WIDTHS.indices.map { width ->
    (listOf(width) + WIDTH_PRIORITY).distinct()
}

/** One unit's seven slots: the display name, then one pattern per plural category. */
private const val SLOTS_PER_UNIT = 7

/** One locale file's `units` subset, sparse the way every partial here is. */
class PartialDurationUnits {
    /** `"<width>/<unit>"` to its seven slots, with null for anything the file does not declare. */
    val units = LinkedHashMap<String, Array<String?>>()

    val isEmpty: Boolean get() = units.values.none { slots -> slots.any { it != null } }
}

fun parseDurationUnits(file: File): PartialDurationUnits {
    val partial = PartialDurationUnits()
    if (!file.exists()) return partial
    val units = parseXml(file).documentElement.child("units") ?: return partial

    for (lengthEl in units.childElements("unitLength")) {
        val width = lengthEl.getAttribute("type")
        if (width !in WIDTHS) continue

        for (unit in lengthEl.childElements("unit")) {
            val type = unit.getAttribute("type")
            if (type !in DURATION_UNITS) continue

            val slots = partial.units.getOrPut("$width/$type") { arrayOfNulls(SLOTS_PER_UNIT) }
            unit.childElements("displayName")
                .firstOrNull { !it.hasAttribute("case") }
                ?.textContent?.cleaned()
                ?.let { if (slots[0] == null) slots[0] = it }

            for (pattern in unit.childElements("unitPattern")) {
                // A `case` is the same kind of marker as an `alt`: present in the
                // file and not the answer. Serbian writes four forms of "3 hours"
                // and only the caseless one is the citation form; taking whichever
                // came first in document order picked the genitive.
                if (pattern.hasAttribute("case")) continue
                val category = CATEGORY_ORDER.indexOf(pattern.getAttribute("count"))
                if (category < 0) continue
                pattern.textContent.cleaned()?.let { if (slots[1 + category] == null) slots[1 + category] = it }
            }
        }
    }
    return partial
}

/** One locale's duration wording, resolved: three width blocks of fourteen units. */
class ResolvedDurationUnits(val blocks: List<List<List<String>>>)

/**
 * Resolves duration units for [id], or null when the locale declares none.
 *
 * Three steps, and each one is here because ICU does it. Checked against
 * `NumberFormatter.unit(...).unitWidth(...)` over every locale, unit and width:
 * the thirty locales of [ICU_GOLDEN_LOCALES] agree in all 1260 cells.
 *
 * The vertical walk includes root, which is load bearing and easy to get wrong.
 * Root declares only a `short` block, and `↑↑↑` under a locale's short unit
 * resolves to it: Spanish writes no short hour of its own, so `3 h` comes from
 * root rather than from Spanish's own narrow `3h` or long `3 horas`.
 *
 * The lateral step then fills what root cannot, because root has no long or
 * narrow block at all. A long that is still empty reads as the locale's short,
 * which is what makes French `duration-night` come out `3 nuits` rather than in
 * English.
 *
 * A locale that declares nothing anywhere in its own chain gets no record, and
 * the runtime falls back to the root entry, which carries English. That is ICU's
 * behaviour too: it ships no bundle for such a locale and answers out of a root
 * built from English, which is why `aa` gives `3 hours` rather than root's
 * placeholder `3 h`.
 *
 * What this does not reproduce is ICU's coverage pruning. For a locale ICU holds
 * at minimal coverage it emits root's short placeholders across all three
 * widths, so `agq` gives `3 c` where CLDR and this table give `3 centuries`; and
 * for a locale ICU ships no unit data for at all, such as `bal_Latn`, it answers
 * in English where CLDR has real wording. Both are ICU's data build rather than
 * a rule in UTS #35, and following them would mean shipping less than CLDR says.
 */
fun Flattener.resolveDurationUnits(id: String, parse: (String) -> PartialDurationUnits): ResolvedDurationUnits? {
    val own = dataChain(id).filter { it != "root" }
    if (own.all { parse(it).isEmpty }) return null

    val merged = LinkedHashMap<String, Array<String?>>()
    for (level in own + "root") {
        for ((key, slots) in parse(level).units) {
            val target = merged.getOrPut(key) { arrayOfNulls(SLOTS_PER_UNIT) }
            for (i in target.indices) if (target[i] == null) target[i] = slots[i]
        }
    }

    val otherSlot = 1 + CATEGORY_ORDER.indexOf("other")
    val blocks = WIDTHS.indices.map { width ->
        DURATION_UNITS.map { unit ->
            List(SLOTS_PER_UNIT) { slot ->
                if (slot == 0) {
                    NAME_FALLBACK[width].firstNotNullOfOrNull { merged["${WIDTHS[it]}/$unit"]?.get(0) }
                } else {
                    PATTERN_FALLBACK[width].firstNotNullOfOrNull { (level, useOther) ->
                        merged["${WIDTHS[level]}/$unit"]?.get(if (useOther) otherSlot else slot)
                    }
                }.orEmpty()
            }
        }
    }
    // A width that resolved to the same wording as long defers to it rather than
    // repeating it, which is the whole reason the reader walks the blocks.
    return ResolvedDurationUnits(blocks.mapIndexed { index, block -> if (index > 0 && block == blocks[0]) emptyList() else block })
}

/** Three width blocks of fourteen units of seven slots, empty where a width matches long. */
fun ResolvedDurationUnits.encode(): String = blocks.joinToString(FIELD_SEPARATOR) { block ->
    if (block.isEmpty()) {
        ""
    } else {
        block.joinToString(LIST_SEPARATOR) { unit -> unit.joinToString(KEY_SEPARATOR) }
    }
}
