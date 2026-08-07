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

package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.Locale
import kotlinx.datetime.DayOfWeek

/**
 * Where a territory starts its week, and which days it rests.
 *
 * This is CLDR's `<weekData>`, which is keyed by territory rather than by
 * language: Portugal starts the week on Sunday whether the screen is in
 * Portuguese or English. Laying out a calendar page, grouping dates into weeks
 * and deciding whether a day is a working day all read it, and all three get
 * the wrong answer from a hardcoded Monday.
 *
 * Days are [DayOfWeek], so Monday is 1 and Sunday is 7 whatever the territory
 * says about where the week begins.
 */
public class WeekInfo(
    /** The day a calendar page starts on: Monday across most of Europe, Sunday in the US. */
    public val firstDayOfWeek: DayOfWeek,
    /**
     * How many days of a week must fall in the new year for it to count as week
     * one. Four across most of Europe, which is the ISO 8601 rule; one in the US.
     */
    public val minimalDaysInFirstWeek: Int,
    /**
     * The rest days, as a set rather than a start and an end.
     *
     * CLDR writes a start day and an end day, and the run between them is always
     * contiguous but not always longer than one day: Iran starts and ends its
     * weekend on Friday, and India rests only on Sunday. A pair invites callers
     * to write `start..end`, which is wrong for a weekend that wraps past
     * Sunday, so the run is resolved before it ships.
     */
    public val weekend: Set<DayOfWeek>,
) {

    override fun toString(): String =
        "WeekInfo(firstDayOfWeek=$firstDayOfWeek, minimalDaysInFirstWeek=$minimalDaysInFirstWeek, weekend=$weekend)"

    public companion object {

        /**
         * CLDR's `001` row: the week starts on Monday, one day of it must fall in
         * the new year, and the weekend is Saturday and Sunday.
         *
         * This is the world default the data itself declares, and it is what the
         * total operations fall back to. Note that it is not the ISO 8601 rule,
         * which agrees on Monday but wants four days rather than one.
         */
        public val WORLD: WeekInfo = WeekInfo(
            firstDayOfWeek = DayOfWeek.MONDAY,
            minimalDaysInFirstWeek = 1,
            weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        )
    }
}

/**
 * A source of week data.
 *
 * Deliberately not a `LocaleDataSource`. That interface reports which locales a
 * source carries, and week data is not keyed by locale at all, so the question
 * has no honest answer here. A locale still resolves to a territory, because
 * that is what callers have, but the resolution is a lookup rather than a
 * property of the data.
 */
public interface WeekInfoSource {

    /**
     * The week data for [locale]'s territory, or null when this build carries
     * none.
     *
     * A locale that names no region is maximised the way likely subtags maximise
     * it, so `en` answers for the United States rather than for the world.
     */
    public fun weekInfoOrNull(locale: Locale): WeekInfo?

    /**
     * The week data for an ISO 3166-1 alpha-2 region code, or null when this
     * build carries none.
     *
     * Separate from [weekInfoOrNull] because it assumes something different
     * about its input: a caller that already holds a country code has the
     * territory, and should not have to build a locale around it.
     */
    public fun weekInfoForRegionOrNull(regionCode: String): WeekInfo?
}

/** [locale]'s week data, falling back to [WeekInfo.WORLD]. */
public fun WeekInfoSource.weekInfo(locale: Locale): WeekInfo = weekInfoOrNull(locale) ?: WeekInfo.WORLD

/** [regionCode]'s week data, falling back to [WeekInfo.WORLD]. */
public fun WeekInfoSource.weekInfoForRegion(regionCode: String): WeekInfo = weekInfoForRegionOrNull(regionCode) ?: WeekInfo.WORLD
