package dev.carcara.kotlinx.locale.codegen

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue
import java.io.File

/**
 * Holds `<weekData>` to what CLDR actually says, against the clone rather than
 * against the encoded output.
 *
 * The encoding is checked by the goldens on the runtime side. What is checked
 * here is the reading: which rows count, which are skipped, and what a territory
 * that declares only half its fields inherits. Those are the decisions the XML
 * does not make obvious, and a wrong one is invisible downstream because every
 * answer still looks like a plausible week.
 *
 * Skipped where the clone is absent, which is every checkout that has not run
 * `:codegen:cloneLocaleRepos`, CI included: `codegen/repos` is gitignored and
 * runs to hundreds of megabytes. The runtime side covers the same data from the
 * committed tables and runs everywhere, so this is a second opinion rather than
 * the only one. Skipping is stated per test rather than left to fail, because a
 * test that fails on a missing input says nothing about the code.
 */
val WeekDataTest by matrixSuite {

    val rootDir = File(
        System.getProperty("kotlinx.locale.rootDir") ?: error("kotlinx.locale.rootDir is not set"),
    )

    val cldrDir: File = reposDir(rootDir).resolve("cldr")

    /**
     * False on a checkout that has not cloned CLDR, which is what CI looks like.
     *
     * Read once, at registration. A local `val` cannot carry a custom getter,
     * and nothing clones a repository partway through a test run anyway.
     */
    val cloned: Boolean = cldrDir.resolve("common/supplemental/supplementalData.xml").isFile

    val supplemental: SupplementalData by lazy { parseSupplemental(cldrDir) }

    fun row(territory: String) = supplemental.weekData.getValue(territory)

    test("theWorldDefaultIsMondayWithOneMinimumDayAndASaturdayWeekend") {
        if (!cloned) return@test
        val world = row("001")
        assertEquals(1, world.firstDay, "001 starts the week on Monday")
        assertEquals(1, world.minDays)
        assertEquals(setOf(6, 7), world.weekend, "001 rests Saturday and Sunday")
    }

    test("theVariantRowDoesNotOverwriteBritain") {
        if (!cloned) return@test
        // supplementalData.xml carries `<firstDay day="sun" territories="GB"
        // alt="variant">` after the row that lists GB among the Monday
        // territories. Reading it would flip every British calendar.
        assertEquals(1, row("GB").firstDay)
        assertEquals(4, row("GB").minDays, "GB needs four days in the year for week one")
    }

    test("theUnitedStatesStartsOnSunday") {
        if (!cloned) return@test
        assertEquals(7, row("US").firstDay)
        assertEquals(1, row("US").minDays)
        assertEquals(setOf(6, 7), row("US").weekend)
    }

    test("aWeekendCanBeASingleDay") {
        if (!cloned) return@test
        // Iran declares both a start and an end of Friday, and India declares
        // only a start, inheriting Sunday as the end from 001. Both collapse to
        // one day, which a start-and-end pair hides and a set does not.
        assertEquals(setOf(5), row("IR").weekend)
        assertEquals(setOf(7), row("IN").weekend)
        assertEquals(setOf(7), row("UG").weekend)
    }

    test("aWeekendCanSitMidweek") {
        if (!cloned) return@test
        assertEquals(setOf(4, 5), row("AF").weekend, "Afghanistan rests Thursday and Friday")
        assertEquals(setOf(5, 6), row("IL").weekend, "Israel rests Friday and Saturday")
    }

    test("everyTerritoryResolvesEveryField") {
        if (!cloned) return@test
        for ((territory, week) in supplemental.weekData) {
            assertTrue(week.firstDay in 1..7, "$territory has an out-of-range first day")
            assertTrue(week.minDays in 1..7, "$territory has an out-of-range minimum")
            assertTrue(week.weekend.isNotEmpty(), "$territory resolved to no weekend at all")
            assertTrue(week.weekend.all { it in 1..7 }, "$territory has an out-of-range weekend day")
        }
    }

    test("theEncodedOverlayAnswersForALocaleWithNoRegion") {
        if (!cloned) return@test
        val encoded = supplemental.encodeWeekData()
        val (territories, overlay) = encoded.split(FIELD_SEPARATOR).let { it[0] to it[1] }

        fun lookup(table: String, key: String): String? = table.split(LIST_SEPARATOR)
            .firstOrNull { it.substringBefore(KEY_SEPARATOR) == key }
            ?.substringAfter(KEY_SEPARATOR)

        // Four characters: first day, minimum days, then a hex weekend mask.
        // Sunday is bit 6 and Saturday bit 5, so a Saturday-Sunday weekend is 0x60.
        assertEquals("7160", lookup(territories, "US"))
        assertEquals("1460", lookup(territories, "GB"))

        // `en` carries no region, so only likely subtags can reach the United
        // States. Without the overlay it would answer the world default.
        assertEquals("7160", lookup(overlay, "en"), "en must maximise to US")
        assertEquals("1460", lookup(overlay, "de"), "de must maximise to DE")

        // The overlay only carries rows that change an answer, so a language
        // whose region agrees with what it would otherwise inherit is absent.
        assertTrue(overlay.isNotEmpty(), "the overlay resolved to nothing")
    }

    test("aScriptRowSurvivesAgreeingWithTheWorldWhenItsLanguageDoesNot") {
        if (!cloned) return@test
        val overlay = supplemental.encodeWeekData().split(FIELD_SEPARATOR)[1]

        fun lookup(key: String): String? = overlay.split(LIST_SEPARATOR)
            .firstOrNull { it.substringBefore(KEY_SEPARATOR) == key }
            ?.substringAfter(KEY_SEPARATOR)

        // Cantonese is the case that breaks a naive "differs from 001" filter.
        // `yue` maximises to Hong Kong, which starts the week on Sunday; the
        // Simplified form maximises to China, which starts on Monday and so
        // matches the world default. Dropping the row for matching the default
        // leaves it inheriting Hong Kong's Sunday from the bare language.
        assertEquals("7160", lookup("yue"), "yue should carry Hong Kong's Sunday")
        assertEquals("1160", lookup("yue_Hans"), "yue_Hans must override it with China's Monday")
    }
}
