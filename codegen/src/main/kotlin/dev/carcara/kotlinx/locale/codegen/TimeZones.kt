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

/** The name of the locale-independent zone table in the bundle. */
const val TIME_ZONE_METADATA_TABLE: String = "timeZoneMetadata"

/** The nine format strings, in the order the record encodes them. */
private val FORMAT_ELEMENTS = listOf(
    "gmtFormat",
    "gmtZeroFormat",
    "gmtUnknownFormat",
    "regionFormat",
    "fallbackFormat",
)

/**
 * The locale-independent zone metadata: which metazone a zone belongs to, which
 * region it is in, which regions have only one zone, and which zones CLDR marks
 * as their region's primary.
 *
 * None of it varies by language, so it rides in the bundle once rather than
 * eleven hundred times.
 *
 * Only the current metazone is kept. The full history with its date ranges is
 * what a formatter needs to name a zone at a past instant, and this library
 * names a zone rather than a zoned instant, so the ranges would be carried for
 * nobody.
 */
fun encodeTimeZoneMetadata(cldrDir: File): String {
    val supplemental = parseXml(cldrDir.resolve("common/supplemental/metaZones.xml")).documentElement
    val metaZones = supplemental.child("metaZones") ?: error("metaZones.xml: no <metaZones>")

    val currentMetazone = LinkedHashMap<String, String>()
    metaZones.child("metazoneInfo")?.let { info ->
        for (timezone in info.childElements("timezone")) {
            val id = timezone.getAttribute("type")
            // Last wins: the entries are in date order and the final one has no
            // `to`, which is the metazone the zone uses now.
            for (uses in timezone.childElements("usesMetazone")) {
                currentMetazone[id] = uses.getAttribute("mzone")
            }
        }
    }

    val primaryZones = LinkedHashSet<String>()
    // A sibling of <metaZones> rather than a child of it.
    supplemental.child("primaryZones")?.let { zones ->
        for (primary in zones.childElements("primaryZone")) {
            primary.textContent?.trim()?.takeIf(String::isNotEmpty)?.let(primaryZones::add)
        }
    }

    // bcp47/timezone.xml is where a zone's region lives; metaZones.xml has the
    // metazone mapping but not the geography.
    val regionOfZone = LinkedHashMap<String, String>()
    val zonesPerRegion = LinkedHashMap<String, MutableSet<String>>()
    val bcp47 = cldrDir.resolve("common/bcp47/timezone.xml")
    check(bcp47.isFile) { "common/bcp47/timezone.xml is missing; widen CLDR_REPO.sparsePaths" }
    val keyword = parseXml(bcp47).documentElement.child("keyword") ?: error("timezone.xml: no <keyword>")
    for (key in keyword.childElements("key")) {
        for (type in key.childElements("type")) {
            val aliases = type.getAttribute("alias").split(' ').filter { it.isNotEmpty() }
            val canonical = aliases.firstOrNull() ?: continue
            // The bcp47 short id starts with the region in lower case, e.g.
            // `uslax` for America/Los_Angeles, `gblon` for Europe/London.
            val name = type.getAttribute("name")
            if (name.length < 2) continue
            // The `utc`, `utcw05` and `unk` ids are not region based. Their
            // first two letters would read as the regions UT and UN, which is
            // how `Etc/GMT+5` came to have a location name at all when UTS #35
            // says a zone with no location takes the offset format.
            if (name.startsWith("utc") || name.startsWith("unk")) continue
            // The explicit attribute wins over the identifier's prefix. The
            // prefix is only a convention and it breaks wherever the short id
            // was named for the city instead: `jeruslm` reads as Jersey, which
            // put Asia/Jerusalem in a two-zone region and cost it `Israel Time`.
            val region = type.getAttribute("region").takeIf { it.length == 2 }?.uppercase()
                ?: name.substring(0, 2).uppercase()
            if (!region.all { it in 'A'..'Z' }) continue
            // Every alias, not just the first. A type lists its deprecated
            // identifier ahead of its current one, so taking the head alone
            // maps Asia/Calcutta and leaves Asia/Kolkata with no region, and
            // the generic location format then reads `Kolkata Time` where CLDR
            // says `India Time`. Europe/Kyiv and Asia/Kathmandu are the same
            // shape.
            for (alias in aliases) regionOfZone[alias] = region
            // Counted once per type, so a region does not look multi-zone
            // merely because one of its zones was renamed. A deprecated type is
            // not counted at all, for the same reason.
            if (type.getAttribute("deprecated") != "true") {
                zonesPerRegion.getOrPut(region) { LinkedHashSet() } += canonical
            }
        }
    }
    val singleZoneRegions = zonesPerRegion.filterValues { it.size == 1 }.keys

    // CLDR lists a primary zone under whichever identifier it used when the
    // entry was written, which for Europe/Kyiv is Europe/Kiev. Adding every
    // alias of each listed zone is what makes the modern spelling resolve.
    val aliasGroups = HashMap<String, Set<String>>()
    for (key in keyword.childElements("key")) {
        for (type in key.childElements("type")) {
            val group = type.getAttribute("alias").split(' ').filter { it.isNotEmpty() }.toSet()
            for (alias in group) aliasGroups[alias] = group
        }
    }
    for (zone in primaryZones.toList()) primaryZones += aliasGroups[zone].orEmpty()

    // CLDR's own tables key a zone by the first alias, which is the identifier
    // it used when the entry was written, while a platform reports the current
    // one. `America/Buenos_Aires` against `America/Argentina/Buenos_Aires`, and
    // `Asia/Calcutta` against `Asia/Kolkata`. Without this map a renamed zone
    // silently loses its exemplar city and any name of its own.
    val cldrIdOfZone = LinkedHashMap<String, String>()
    for ((alias, group) in aliasGroups) {
        val cldrId = group.firstOrNull() ?: continue
        if (alias != cldrId) cldrIdOfZone[alias] = cldrId
    }

    check(currentMetazone.size > 300) { "metaZones.xml: implausibly few zones (${currentMetazone.size})" }
    println(
        "[codegen] time zone metadata: ${currentMetazone.size} zones in ${zonesPerRegion.size} regions, " +
            "${singleZoneRegions.size} of them single-zone, ${primaryZones.size} primary zones",
    )

    fun entries(map: Map<String, String>): String =
        map.entries.sortedBy { it.key }.joinToString(LIST_SEPARATOR) { (key, value) -> key + KEY_SEPARATOR + value }

    return listOf(
        entries(currentMetazone),
        entries(regionOfZone),
        singleZoneRegions.sorted().joinToString(LIST_SEPARATOR),
        primaryZones.sorted().joinToString(LIST_SEPARATOR),
        entries(cldrIdOfZone),
    ).joinToString(FIELD_SEPARATOR)
}

/** One locale file's `<timeZoneNames>` subset, sparse the way every partial here is. */
class PartialTimeZoneNames {
    var hourFormat: String? = null
    val formats = LinkedHashMap<String, String>()
    var regionStandard: String? = null
    var regionDaylight: String? = null

    /** tzid -> localized city, only what this file declares. */
    val cities = LinkedHashMap<String, String>()

    /** `"<id>#<form><type>"` -> name, for a zone's own overrides. */
    val zoneNames = LinkedHashMap<String, String>()

    /** The same, keyed by metazone. */
    val metazoneNames = LinkedHashMap<String, String>()
}

fun parseTimeZoneNames(file: File): PartialTimeZoneNames {
    val partial = PartialTimeZoneNames()
    val names = parseXml(file).documentElement.child("dates")?.child("timeZoneNames") ?: return partial

    names.child("hourFormat")?.textContent?.cleaned()?.let { partial.hourFormat = it }
    for (element in FORMAT_ELEMENTS) {
        for (candidate in names.childElements(element)) {
            if (candidate.hasAttribute("type")) continue
            candidate.textContent.cleaned()?.let { partial.formats.putIfAbsent(element, it) }
        }
    }
    for (region in names.childElements("regionFormat")) {
        when (region.getAttribute("type")) {
            "standard" -> region.textContent.cleaned()?.let { if (partial.regionStandard == null) partial.regionStandard = it }
            "daylight" -> region.textContent.cleaned()?.let { if (partial.regionDaylight == null) partial.regionDaylight = it }
        }
    }

    fun readNames(element: org.w3c.dom.Element, id: String, target: MutableMap<String, String>) {
        for (form in listOf("long" to "l", "short" to "s")) {
            val block = element.child(form.first) ?: continue
            for (type in listOf("generic" to "g", "standard" to "s", "daylight" to "d")) {
                val value = block.child(type.first)?.textContent ?: continue
                // UTS #35 blocks inheritance of a form with three U+2205, which
                // has to survive as an explicit empty rather than as an absence.
                val cleaned = if (value.trim() == "∅∅∅") "" else value.cleaned() ?: continue
                target.putIfAbsent("$id#${form.second}${type.second}", cleaned)
            }
        }
    }

    for (zone in names.childElements("zone")) {
        val id = zone.getAttribute("type")
        if (id.isEmpty()) continue
        zone.child("exemplarCity")?.textContent?.cleaned()?.let { partial.cities.putIfAbsent(id, it) }
        readNames(zone, id, partial.zoneNames)
    }
    for (metazone in names.childElements("metazone")) {
        val id = metazone.getAttribute("type")
        if (id.isEmpty()) continue
        readNames(metazone, id, partial.metazoneNames)
    }
    return partial
}

/** Fully resolved zone format strings for one locale: nine fields. */
fun Flattener.resolveTimeZoneFormats(id: String, parse: (String) -> PartialTimeZoneNames): String {
    val chain = dataChain(id).map(parse)
    fun first(select: (PartialTimeZoneNames) -> String?): String? = chain.firstNotNullOfOrNull(select)

    val hourFormat = first { it.hourFormat } ?: "+HH:mm;-HH:mm"
    val regionFormat = first { it.formats["regionFormat"] } ?: "{0}"
    return listOf(
        hourFormat.substringBefore(';'),
        hourFormat.substringAfter(';', "-HH:mm"),
        first { it.formats["gmtFormat"] } ?: "GMT{0}",
        first { it.formats["gmtZeroFormat"] } ?: "GMT",
        first { it.formats["gmtUnknownFormat"] } ?: "GMT+?",
        regionFormat,
        first { it.regionStandard } ?: regionFormat,
        first { it.regionDaylight } ?: regionFormat,
        first { it.formats["fallbackFormat"] } ?: "{1} ({0})",
    ).joinToString(FIELD_SEPARATOR)
}

/** Sparse per-locale zone name payloads: the parent tag, then zone and metazone names. */
fun buildTimeZoneNamePayloads(flattener: Flattener, parse: (String) -> PartialTimeZoneNames): Map<String, String> {
    fun entries(map: Map<String, String>): String = map.entries
        .sortedBy { it.key }
        .joinToString(LIST_SEPARATOR) { (key, value) -> key + KEY_SEPARATOR + value }

    val payloads = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        val partial = parse(id)
        val parentTag = flattener.dataChain(id).getOrNull(1)?.let(::canonicalTag).orEmpty()
        payloads[canonicalTag(id)] = parentTag + FIELD_SEPARATOR +
            "" + FIELD_SEPARATOR +
            entries(partial.zoneNames) + FIELD_SEPARATOR +
            entries(partial.metazoneNames)
    }
    return payloads
}

/** Sparse per-locale exemplar city payloads, in their own section so they can ship separately. */
fun buildTimeZoneCityPayloads(flattener: Flattener, parse: (String) -> PartialTimeZoneNames): Map<String, String> {
    val payloads = LinkedHashMap<String, String>()
    for (id in listOf("root") + flattener.localeIds) {
        val partial = parse(id)
        val parentTag = flattener.dataChain(id).getOrNull(1)?.let(::canonicalTag).orEmpty()
        val entries = partial.cities.entries
            .sortedBy { it.key }
            .joinToString(LIST_SEPARATOR) { (id2, city) -> id2 + KEY_SEPARATOR + city }
        payloads[canonicalTag(id)] = parentTag + FIELD_SEPARATOR + entries
    }
    return payloads
}
