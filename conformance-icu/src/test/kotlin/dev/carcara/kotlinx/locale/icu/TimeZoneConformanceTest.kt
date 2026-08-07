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

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import com.ibm.icu.text.TimeZoneNames
import com.ibm.icu.util.ULocale
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.test.assertTrue
import dev.carcara.kotlinx.locale.timezone.TimeZoneNameStyle
import dev.carcara.kotlinx.locale.timezone.cldr.CldrTimeZone
import dev.carcara.kotlinx.locale.timezone.cldr.cities.CldrTimeZoneCities
import kotlinx.datetime.TimeZone

/**
 * The two time zone tables, neither of which had a live oracle.
 *
 * Exemplar cities had none at all: 1122 locales times roughly 450 zones of
 * translated city names, shipped on the strength of the generator being right.
 * That is the single largest unchecked table left in the library, and "Londres"
 * versus "London" is exactly the kind of thing a misread CLDR alias produces
 * without breaking anything that would fail to compile.
 *
 * Zone names had a golden covering thirty locales. The rest were unchecked.
 */
val TimeZoneConformanceTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val cityTags = CldrTimeZoneCities.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    val nameTags = CldrTimeZone.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    // Canonical zones only. tzdb ships hundreds of aliases, both sides resolve an
    // alias to the same canonical zone, and comparing them would run the same
    // comparison several times under different names while reading as breadth.
    val zones = com.ibm.icu.util.TimeZone.getAvailableIDs()
        .filter { com.ibm.icu.util.TimeZone.getCanonicalID(it) == it }
        .filter { runCatching { TimeZone.of(it) }.isSuccess }
        .sorted()

    test("the zone set is the whole of tzdb") {
        assertTrue(
            zones.size > 300,
            "only ${zones.size} canonical zones resolve on both sides, which suggests the filter is " +
                "wrong rather than that tzdb shrank",
        )
    }

    test("exemplar cities agree with ICU") {
        // Every zone, not a sample: a city name is per zone rather than per
        // metazone, so there is no smaller set that covers the table.
        val comparison = DomainComparison("exemplar-cities")
        for (tag in cityTags) {
            val locale = IcuHarness.locale(tag)
            val icu = TimeZoneNames.getInstance(IcuHarness.uLocale(tag))
            for (id in zones) {
                val ours = CldrTimeZoneCities.exemplarCityOrNull(TimeZone.of(id), locale) ?: continue
                val theirs = icu.getExemplarLocationName(id) ?: continue
                comparison.compare(tag, id, ours, theirs) { classifyZone(tag) }
            }
        }
        comparison.settle(minimumCompared = cityTags.size * 50L)
    }

    test("time zone names agree with ICU") {
        // One zone per metazone. Zone names are stored against metazones, so a
        // second zone in the same metazone re-asks a question already answered;
        // the grouping is derived from ICU's own English names rather than from a
        // hand-written list, so it follows CLDR when metazones are added.
        val english = TimeZoneNames.getInstance(ULocale.ENGLISH)
        val representatives = zones
            .groupBy { id ->
                ZONE_STYLES.joinToString("|") { (_, type) -> english.getDisplayName(id, type, REFERENCE_MILLIS) ?: "" }
            }
            .values
            .map { it.first() }
            .sorted()

        val comparison = DomainComparison("timezone-names")
        for (tag in nameTags) {
            val locale = IcuHarness.locale(tag)
            val icu = TimeZoneNames.getInstance(IcuHarness.uLocale(tag))
            for (id in representatives) {
                val zone = TimeZone.of(id)
                for ((style, type) in ZONE_STYLES) {
                    val ours = CldrTimeZone.displayNameOrNull(zone, style, null, locale) ?: continue
                    val theirs = icu.getDisplayName(id, type, REFERENCE_MILLIS) ?: continue
                    comparison.compare(tag, "$id/$style", ours, theirs) { classifyZone(tag) }
                }
            }
        }
        comparison.settle(minimumCompared = nameTags.size * 10L)
    }
}

private val ZONE_STYLES = listOf(
    TimeZoneNameStyle.GENERIC_LONG to TimeZoneNames.NameType.LONG_GENERIC,
    TimeZoneNameStyle.GENERIC_SHORT to TimeZoneNames.NameType.SHORT_GENERIC,
    TimeZoneNameStyle.STANDARD_LONG to TimeZoneNames.NameType.LONG_STANDARD,
    TimeZoneNameStyle.STANDARD_SHORT to TimeZoneNames.NameType.SHORT_STANDARD,
    TimeZoneNameStyle.DAYLIGHT_LONG to TimeZoneNames.NameType.LONG_DAYLIGHT,
    TimeZoneNameStyle.DAYLIGHT_SHORT to TimeZoneNames.NameType.SHORT_DAYLIGHT,
)

private fun classifyZone(tag: String): Divergence? = if (!IcuHarness.icuCarries(tag)) Divergence.BUNDLE_FALLBACK else null
