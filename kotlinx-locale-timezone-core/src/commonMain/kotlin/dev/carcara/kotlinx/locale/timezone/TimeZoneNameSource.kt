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

package dev.carcara.kotlinx.locale.timezone

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset

/**
 * The forms UTS #35 defines for naming a zone, in the vocabulary of that
 * document rather than of the pattern letters that select them.
 */
public enum class TimeZoneNameStyle {

    /** Wall time, no location: `Pacific Time`. The `vvvv` field. */
    GENERIC_LONG,

    /** The same, abbreviated: `PT`. The `v` field. */
    GENERIC_SHORT,

    /** A specific offset: `Pacific Standard Time`. The `zzzz` field. */
    STANDARD_LONG,

    /** The same, abbreviated: `PST`. The `z` field. */
    STANDARD_SHORT,

    /** The daylight counterpart: `Pacific Daylight Time`. */
    DAYLIGHT_LONG,

    /** The same, abbreviated: `PDT`. */
    DAYLIGHT_SHORT,

    /** Wall time by place: `Los Angeles Time`, `Japan Time`. The `VVVV` field. */
    LOCATION,

    /** A constant offset in the locale's own words and digits: `GMT-08:00`. */
    OFFSET_LONG,

    /** The same, as short as the locale writes it: `GMT-8`. */
    OFFSET_SHORT,
    ;

    public companion object
}

/**
 * A source of localized time zone names.
 *
 * The zone and the offset are separate arguments because they answer different
 * questions. The zone decides which name, the offset decides which of the
 * standard and daylight forms and what the offset styles print, and a caller
 * that knows which form it wants should not have to supply an instant to get it.
 */
public interface TimeZoneNameSource : LocaleDataSource {

    /**
     * The name of [zone] in [style] for [locale], or `null` when this source
     * carries nothing for it.
     *
     * [offset] is what the offset styles print. `null` means unknown, which is
     * what CLDR's `gmtUnknownFormat` exists for.
     */
    public fun displayNameOrNull(zone: TimeZone, style: TimeZoneNameStyle, offset: UtcOffset?, locale: Locale): String?

    /**
     * The localized city that stands for [zone], for a picker.
     *
     * `null` when this build did not take the exemplar cities, which is a
     * separate artifact because they are the largest table here.
     */
    public fun exemplarCityOrNull(zone: TimeZone, locale: Locale): String?

    public companion object
}

/**
 * [zone] in [style] for [locale].
 *
 * Falls back through the ladder UTS #35 prescribes: a missing name degrades to
 * the localized GMT format, and a zone with no offset to hand degrades to its
 * tzdb identifier rather than to nothing.
 */
public fun TimeZoneNameSource.displayName(
    zone: TimeZone,
    style: TimeZoneNameStyle = TimeZoneNameStyle.GENERIC_LONG,
    offset: UtcOffset? = null,
    locale: Locale = Locale.current,
): String = displayNameOrNull(zone, style, offset, locale)
    ?: displayNameOrNull(zone, TimeZoneNameStyle.OFFSET_LONG, offset, locale)
    ?: zone.id

/** The localized city for [zone]; falls back to the last part of the tzdb id, as the spec says. */
public fun TimeZoneNameSource.exemplarCity(zone: TimeZone, locale: Locale = Locale.current): String =
    exemplarCityOrNull(zone, locale) ?: zone.id.substringAfterLast('/').replace('_', ' ')

/** Answers from [primary], and from [fallback] wherever primary has nothing. */
public class FallbackTimeZoneNames(private val primary: TimeZoneNameSource, private val fallback: TimeZoneNameSource) :
    TimeZoneNameSource {

    override val supportedLocales: Set<Locale> get() = primary.supportedLocales + fallback.supportedLocales

    override fun displayNameOrNull(zone: TimeZone, style: TimeZoneNameStyle, offset: UtcOffset?, locale: Locale): String? =
        primary.displayNameOrNull(zone, style, offset, locale) ?: fallback.displayNameOrNull(zone, style, offset, locale)

    override fun exemplarCityOrNull(zone: TimeZone, locale: Locale): String? =
        primary.exemplarCityOrNull(zone, locale) ?: fallback.exemplarCityOrNull(zone, locale)

    public companion object
}
