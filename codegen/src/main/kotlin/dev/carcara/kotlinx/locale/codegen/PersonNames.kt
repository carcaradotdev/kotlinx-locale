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
 * The person name grid, in the order the encoded record carries it.
 *
 * Forty-two cells rather than the fifty-four the four axes suggest, because
 * `sorting` is only ever `referring`: CLDR declares no sorted form of a
 * monogram or of how you address someone.
 */
val PERSON_NAME_CELLS: List<PersonNameCell> = buildList {
    for (order in listOf("givenFirst", "surnameFirst")) {
        for (length in listOf("long", "medium", "short")) {
            for (usage in listOf("referring", "addressing", "monogram")) {
                for (formality in listOf("formal", "informal")) {
                    add(PersonNameCell(order, length, usage, formality))
                }
            }
        }
    }
    for (length in listOf("long", "medium", "short")) {
        for (formality in listOf("formal", "informal")) {
            add(PersonNameCell("sorting", length, "referring", formality))
        }
    }
}

data class PersonNameCell(val order: String, val length: String, val usage: String, val formality: String)

private val CELL_INDEX: Map<PersonNameCell, Int> = PERSON_NAME_CELLS.withIndex().associate { (i, c) -> c to i }

/** One locale file's `personNames`, before inheritance. */
class PartialPersonNames {
    /**
     * Cell to its `namePattern` list, indexed by alt slot.
     *
     * A list rather than one string because 55 cells in CLDR 48.2 carry two
     * patterns under `alt="1"`, and inheritance runs per slot: Spanish declares
     * the second and inherits the first.
     */
    val patterns = HashMap<PersonNameCell, Array<String?>>()

    /** Cell to the cell it aliases, which is how root fills 37 of its 42. */
    val aliases = HashMap<PersonNameCell, PersonNameCell>()

    var givenFirstLocales: String? = null
    var surnameFirstLocales: String? = null
    var defaultLength: String? = null
    var defaultFormality: String? = null

    /**
     * Null means "not declared", which is not the same as declared empty.
     * Japanese declares an empty native replacement, which is what joins a
     * family and given name with nothing between them.
     */
    var nativeSpace: String? = null
    var foreignSpace: String? = null
    var initialPattern: String? = null
    var initialSequence: String? = null
}

/** The most alt slots any cell carries; two in CLDR 48.2, with room to grow. */
private const val MAX_ALT_SLOTS = 4

private val ALIAS_ATTRIBUTE = Regex("@(order|length|usage|formality)='([a-zA-Z]+)'")

fun parsePersonNames(file: File): PartialPersonNames {
    val data = PartialPersonNames()
    if (!file.isFile) return data
    val personNames = parseXml(file).documentElement.child("personNames") ?: return data

    for (order in personNames.childElements("nameOrderLocales")) {
        if (order.hasAttribute("alt")) continue
        val value = order.textContent.cleaned() ?: continue
        when (order.getAttribute("order")) {
            "givenFirst" -> if (data.givenFirstLocales == null) data.givenFirstLocales = value
            "surnameFirst" -> if (data.surnameFirstLocales == null) data.surnameFirstLocales = value
        }
    }

    for (parameter in personNames.childElements("parameterDefault")) {
        if (parameter.hasAttribute("alt")) continue
        val value = parameter.textContent.cleaned() ?: continue
        when (parameter.getAttribute("parameter")) {
            "length" -> if (data.defaultLength == null) data.defaultLength = value
            "formality" -> if (data.defaultFormality == null) data.defaultFormality = value
        }
    }

    // Read with textContent rather than cleaned() first, because the empty
    // string is a meaningful value here and only the arrow marker means inherit.
    personNames.child("nativeSpaceReplacement")?.let {
        if (it.textContent != INHERITANCE_MARKER) data.nativeSpace = it.textContent
    }
    personNames.child("foreignSpaceReplacement")?.let {
        if (it.textContent != INHERITANCE_MARKER) data.foreignSpace = it.textContent
    }

    for (pattern in personNames.childElements("initialPattern")) {
        if (pattern.hasAttribute("alt")) continue
        val value = pattern.textContent.cleaned() ?: continue
        when (pattern.getAttribute("type")) {
            "initial" -> if (data.initialPattern == null) data.initialPattern = value
            "initialSequence" -> if (data.initialSequence == null) data.initialSequence = value
        }
    }

    for (personName in personNames.childElements("personName")) {
        val cell = PersonNameCell(
            order = personName.getAttribute("order"),
            length = personName.getAttribute("length"),
            usage = personName.getAttribute("usage"),
            formality = personName.getAttribute("formality"),
        )
        if (cell !in CELL_INDEX) continue

        personName.child("alias")?.let { alias ->
            val target = ALIAS_ATTRIBUTE.findAll(alias.getAttribute("path")).associate { it.groupValues[1] to it.groupValues[2] }
            if (target.size == 4) {
                data.aliases.putIfAbsent(
                    cell,
                    PersonNameCell(
                        order = target.getValue("order"),
                        length = target.getValue("length"),
                        usage = target.getValue("usage"),
                        formality = target.getValue("formality"),
                    ),
                )
            }
            return@let
        }

        val slots = data.patterns.getOrPut(cell) { arrayOfNulls(MAX_ALT_SLOTS) }
        for (namePattern in personName.childElements("namePattern")) {
            // No alt is slot 0, alt="1" is slot 1, and so on.
            val slot = namePattern.getAttribute("alt").takeIf(String::isNotEmpty)?.toIntOrNull() ?: 0
            if (slot !in 0 until MAX_ALT_SLOTS || slots[slot] != null) continue
            slots[slot] = namePattern.textContent.cleaned()
        }
    }

    return data
}

class ResolvedPersonNames(
    /** Indexed by [PERSON_NAME_CELLS]; each cell is its alt patterns in slot order. */
    val cells: List<List<String>>,
    val givenFirstLocales: String,
    val surnameFirstLocales: String,
    val defaultLength: String,
    val defaultFormality: String,
    val nativeSpace: String,
    val foreignSpace: String,
    val initialPattern: String,
    val initialSequence: String,
)

/**
 * Person name patterns for one locale.
 *
 * Two things here are not the usual chain walk. Inheritance runs per alt slot
 * rather than per cell, because a cell can declare its second pattern and
 * inherit its first. And root fills 37 of its 42 cells with a lateral alias to
 * another cell rather than with a pattern, so the graph is folded flat once the
 * chain is exhausted; a locale that inherits everything would otherwise arrive
 * with five patterns instead of forty-two.
 */
fun Flattener.resolvePersonNames(id: String, parse: (String) -> PartialPersonNames): ResolvedPersonNames {
    val slots = PERSON_NAME_CELLS.associateWith { arrayOfNulls<String>(MAX_ALT_SLOTS) }
    val aliases = HashMap<PersonNameCell, PersonNameCell>()
    var givenFirst: String? = null
    var surnameFirst: String? = null
    var defaultLength: String? = null
    var defaultFormality: String? = null
    var nativeSpace: String? = null
    var foreignSpace: String? = null
    var initialPattern: String? = null
    var initialSequence: String? = null

    for (level in dataChain(id)) {
        val p = parse(level)
        for (cell in PERSON_NAME_CELLS) {
            val target = slots.getValue(cell)
            val source = p.patterns[cell] ?: continue
            for (slot in 0 until MAX_ALT_SLOTS) {
                if (target[slot] == null) target[slot] = source[slot]
            }
        }
        for ((cell, target) in p.aliases) aliases.putIfAbsent(cell, target)
        if (givenFirst == null) givenFirst = p.givenFirstLocales
        if (surnameFirst == null) surnameFirst = p.surnameFirstLocales
        if (defaultLength == null) defaultLength = p.defaultLength
        if (defaultFormality == null) defaultFormality = p.defaultFormality
        if (nativeSpace == null) nativeSpace = p.nativeSpace
        if (foreignSpace == null) foreignSpace = p.foreignSpace
        if (initialPattern == null) initialPattern = p.initialPattern
        if (initialSequence == null) initialSequence = p.initialSequence
    }

    // Fold the alias graph, with a hop limit rather than a visited set: an alias
    // chain is two or three long and a cycle would otherwise hang generation.
    //
    // Per slot rather than per cell, because inheritance in LDML is per path and
    // a slot is part of the path. Catalan declares the first pattern of five of
    // its six sorting cells and writes the arrow marker for the second, and the
    // marker resolves through root's lateral alias back to Catalan's own
    // `sorting-long-formal`. Gating the walk on the first slot being absent
    // skipped every one of them, which cost the sorting forms their comma.
    for (cell in PERSON_NAME_CELLS) {
        val target = slots.getValue(cell)
        for (slot in 0 until MAX_ALT_SLOTS) {
            if (target[slot] != null) continue
            var current = cell
            var hops = 0
            while (hops++ < PERSON_NAME_CELLS.size) {
                current = aliases[current] ?: break
                val resolved = slots[current] ?: break
                if (resolved[slot] != null) {
                    target[slot] = resolved[slot]
                    break
                }
            }
        }
    }

    val cells = PERSON_NAME_CELLS.map { cell ->
        val resolved = slots.getValue(cell).filterNotNull()
        check(resolved.isNotEmpty()) { "$id: no pattern for $cell" }
        resolved
    }

    return ResolvedPersonNames(
        cells = cells,
        givenFirstLocales = givenFirst.orEmpty(),
        surnameFirstLocales = surnameFirst.orEmpty(),
        defaultLength = defaultLength ?: "medium",
        defaultFormality = defaultFormality ?: "formal",
        nativeSpace = nativeSpace ?: " ",
        foreignSpace = foreignSpace ?: " ",
        initialPattern = initialPattern ?: "{0}.",
        initialSequence = initialSequence ?: "{0} {1}",
    )
}

/** Forty-two cells, then the eight auxiliary values, all positional. */
fun ResolvedPersonNames.encode(): String = listOf(
    cells.joinToString(LIST_SEPARATOR) { it.joinToString(KEY_SEPARATOR) },
    givenFirstLocales,
    surnameFirstLocales,
    defaultLength,
    defaultFormality,
    nativeSpace,
    foreignSpace,
    initialPattern,
    initialSequence,
).joinToString(FIELD_SEPARATOR)
