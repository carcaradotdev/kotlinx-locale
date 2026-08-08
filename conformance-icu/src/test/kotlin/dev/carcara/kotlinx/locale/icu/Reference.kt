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

package dev.carcara.kotlinx.locale.icu

import java.time.ZoneOffset

/**
 * The one instant every comparison that needs a date is written against.
 *
 * Fixed rather than taken from the clock, and shared rather than repeated. A
 * comparison that reads the current time is a comparison that can fail on a
 * particular day: 29 February exists in some calendars and not others, the last
 * day of a month renders differently under skeletons that carry a day of week,
 * and a daylight boundary moves which zone name applies. All three have bitten
 * conformance suites before.
 *
 * 14 March 2026 at 15:30:45 UTC. A Saturday, so day-of-week names are exercised;
 * mid-month and mid-year, so nothing sits on a boundary; and the time has a
 * non-zero second, which is what separates a MEDIUM time pattern from a SHORT
 * one.
 */
const val REFERENCE_YEAR: Int = 2026
const val REFERENCE_MONTH: Int = 3
const val REFERENCE_DAY: Int = 14
const val REFERENCE_HOUR: Int = 15
const val REFERENCE_MINUTE: Int = 30
const val REFERENCE_SECOND: Int = 45

/** [REFERENCE_YEAR] and the rest as epoch milliseconds, which is what ICU takes. */
val REFERENCE_MILLIS: Long = utcMillis(REFERENCE_YEAR, REFERENCE_MONTH, REFERENCE_DAY, REFERENCE_HOUR, REFERENCE_MINUTE, REFERENCE_SECOND)

/** The same instant as a [java.util.Date], which is what `DateFormat` takes. */
val REFERENCE_DATE: java.util.Date = java.util.Date(REFERENCE_MILLIS)

fun utcMillis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): Long =
    java.time.LocalDateTime.of(year, month, day, hour, minute, second).toInstant(ZoneOffset.UTC).toEpochMilli()

/**
 * UTC, as ICU spells it.
 *
 * Every comparison that renders a date pins this. Left to the default, a date
 * near midnight formats as one day on a machine in Tokyo and the previous day on
 * one in Los Angeles, and the test that catches it is the one that runs in CI
 * rather than the one that ran locally.
 */
val ICU_UTC: com.ibm.icu.util.TimeZone = com.ibm.icu.util.TimeZone.GMT_ZONE

/** A Gregorian calendar in UTC, which is the only calendar this library implements. */
fun gregorianUtc(): com.ibm.icu.util.Calendar = com.ibm.icu.util.GregorianCalendar(ICU_UTC)
