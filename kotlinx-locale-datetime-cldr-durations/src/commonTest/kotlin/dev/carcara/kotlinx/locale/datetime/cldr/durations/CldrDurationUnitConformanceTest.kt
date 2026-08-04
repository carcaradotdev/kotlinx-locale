package dev.carcara.kotlinx.locale.datetime.cldr.durations

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.durations.conformance.icuDurationUnitGolden
import dev.carcara.kotlinx.locale.datetime.cldr.durations.conformance.icuDurationUnitGoldenCases
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.DurationUnit
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.UnitWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every cell ICU was asked about, asked again here.
 *
 * The goldens come from `NumberFormatter.unit(...).unitWidth(...)` over thirty
 * locales, fourteen units, three widths and eight values. Nothing is excluded:
 * the fallback rules this table is built on were read off this fixture, so an
 * exclusion here would be the fixture agreeing with itself.
 */
class CldrDurationUnitConformanceTest {

    private val units = DurationUnit.entries.associateBy { "duration-" + it.name.lowercase() }

    @Test
    fun matchesIcu() {
        val failures = ArrayList<String>()
        var checked = 0
        for ((tag, answers) in icuDurationUnitGolden) {
            val locale = Locale.forLanguageTag(tag)
            assertEquals(icuDurationUnitGoldenCases.size, answers.size, "$tag answered a different number of cases")
            for ((index, case) in icuDurationUnitGoldenCases.withIndex()) {
                val (unitType, widthName, value) = case
                val actual = durationFormat(value, units.getValue(unitType), UnitWidth.valueOf(widthName), locale)
                checked++
                if (actual != answers[index]) {
                    failures.add("$tag $unitType $widthName $value: expected <${answers[index]}> got <$actual>")
                }
            }
        }
        assertTrue(
            failures.isEmpty(),
            "${failures.size} of $checked cells disagree with ICU:\n" + failures.take(30).joinToString("\n"),
        )
    }

    @Test
    fun theGoldenCoversWhatItClaims() {
        assertEquals(30, icuDurationUnitGolden.size, "the golden should cover the shared ICU locale set")
        assertEquals(14 * 3 * 8, icuDurationUnitGoldenCases.size, "14 units x 3 widths x 8 values")
        assertEquals(14, units.size)
        assertTrue(icuDurationUnitGoldenCases.map { it.first }.distinct().all { it in units })
    }
}
