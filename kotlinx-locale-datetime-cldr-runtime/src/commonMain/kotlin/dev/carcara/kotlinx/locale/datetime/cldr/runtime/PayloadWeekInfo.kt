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

package dev.carcara.kotlinx.locale.datetime.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.WeekInfo
import dev.carcara.kotlinx.locale.datetime.WeekInfoSource
import dev.carcara.kotlinx.locale.internal.ENTRY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.KEY_SEPARATOR
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

/**
 * Week data over one packed table.
 *
 * The table has two fields. The first is every territory CLDR names; the second
 * is the languages whose likely region answers something other than the world
 * default, which is the only way a locale carrying no region can be resolved
 * without shipping the whole likely-subtags table.
 *
 * Each value is four characters: the first day, the minimum days, and a two
 * digit hex weekend mask over the same ISO day numbers.
 */
@InternalKotlinxLocaleApi
public class PayloadWeekInfo(table: String) : WeekInfoSource {

    private val byTerritory: Map<String, String>
    private val byLanguage: Map<String, String>

    init {
        val fields = table.split(FIELD_SEPARATOR)
        byTerritory = decode(fields.getOrNull(0))
        byLanguage = decode(fields.getOrNull(1))
    }

    private fun decode(field: String?): Map<String, String> {
        if (field.isNullOrEmpty()) return emptyMap()
        val entries = field.split(ENTRY_SEPARATOR)
        val decoded = HashMap<String, String>(entries.size)
        for (entry in entries) {
            val separator = entry.indexOf(KEY_SEPARATOR)
            if (separator <= 0) continue
            decoded[entry.substring(0, separator)] = entry.substring(separator + 1)
        }
        return decoded
    }

    private fun unpack(packed: String): WeekInfo? {
        if (packed.length < 4) return null
        val firstDay = packed[0].digitToIntOrNull() ?: return null
        val minDays = packed[1].digitToIntOrNull() ?: return null
        val mask = packed.substring(2).toIntOrNull(16) ?: return null
        if (firstDay !in 1..7 || minDays !in 1..7) return null

        val weekend = LinkedHashSet<DayOfWeek>()
        // Walk from the first day rather than from Monday, so the set iterates in
        // the order the territory itself reads a week.
        for (offset in 0..6) {
            val day = (firstDay - 1 + offset) % 7 + 1
            if (mask and (1 shl (day - 1)) != 0) weekend.add(dayOf(day))
        }
        return WeekInfo(dayOf(firstDay), minDays, weekend)
    }

    private fun dayOf(isoDayNumber: Int): DayOfWeek = DayOfWeek.entries.first { it.isoDayNumber == isoDayNumber }

    override fun weekInfoForRegionOrNull(regionCode: String): WeekInfo? {
        val packed = byTerritory[regionCode.uppercase()] ?: byTerritory[WORLD] ?: return null
        return unpack(packed)
    }

    override fun weekInfoOrNull(locale: Locale): WeekInfo? {
        // A region on the locale is the answer outright. Without one, only the
        // overlay can say where the language is spoken, and its absence means the
        // language agrees with the world default rather than that it is unknown.
        locale.region?.let { return weekInfoForRegionOrNull(it) }

        val script = locale.script
        val packed = (if (script != null) byLanguage["${locale.language}_$script"] else null)
            ?: byLanguage[locale.language]
            ?: byTerritory[WORLD]
            ?: return null
        return unpack(packed)
    }

    private companion object {
        /** CLDR's world default row, which every other lookup falls back to. */
        const val WORLD = "001"
    }
}
