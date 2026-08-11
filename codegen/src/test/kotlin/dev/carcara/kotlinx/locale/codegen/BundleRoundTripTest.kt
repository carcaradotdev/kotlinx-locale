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

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertFailsWith
import dev.carcara.kotlinx.locale.test.assertFalse
import dev.carcara.kotlinx.locale.test.assertTrue
import java.io.File

/**
 * Pins the published bundle to the checked-in sources.
 *
 * The shipped `-types` and `-cldr-full` artifacts and anything the Gradle plugin
 * generates must come out of one code path, or "the split and the definitions
 * are the same" stops being true. What guarantees it is that there is exactly
 * one set of emitters and one bundle; what proves it is this: regenerate every
 * shipped source from the bundle alone, with no CLDR clone in sight, and
 * compare byte for byte.
 *
 * A failure means either the bundle is stale — someone changed an emitter or
 * the CLDR pin without running `./gradlew :codegen:generateLocaleData` — or the
 * bundle cannot carry something the sources need, which is the more interesting
 * case, because the plugin would silently generate it wrong.
 */
val BundleRoundTripTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {
    val rootDir = File(
        System.getProperty("kotlinx.locale.rootDir") ?: error("kotlinx.locale.rootDir is not set"),
    )

    fun createTempDirectory(): File = File.createTempFile("kotlinx-locale-roundtrip", "").let { file ->
        file.delete()
        file.mkdirs()
        file
    }
    test("theBundleRegeneratesEveryShippedSourceByteForByte") {
        val bundle = bundleFile(rootDir).bufferedReader().use(LocaleDataBundle::readFrom)
        assertEquals(1121, bundle.localeTags.size, "the bundle lost locales")

        val regenerated = createTempDirectory()
        try {
            generateSources(
                bundle = bundle,
                roots = shippedRoots(regenerated),
            )

            var compared = 0
            val modules = listOf(
                "kotlinx-locale-types",
                "kotlinx-locale-country-types",
                "kotlinx-locale-country-cldr-full",
                "kotlinx-locale-currency-types",
                "kotlinx-locale-currency-cldr-full",
                "kotlinx-locale-datetime-cldr-full",
                "kotlinx-locale-datetime-cldr-skeletons",
            )
            for (module in modules) {
                val fresh = regenerated.resolve("$module/src/commonMain/kotlin")
                val shipped = rootDir.resolve("$module/src/commonMain/kotlin")
                for (file in fresh.walkTopDown().filter(File::isFile)) {
                    val relative = file.relativeTo(fresh).path
                    val committed = shipped.resolve(relative)
                    assertTrue(committed.isFile, "$module/$relative is generated but not checked in")
                    assertEquals(committed.readText(), file.readText(), "$module/$relative drifted from the bundle")
                    compared++
                }
            }
            assertTrue(compared > 400, "expected the whole generated tree, compared only $compared files")
        } finally {
            regenerated.deleteRecursively()
        }
    }

    test("narrowingKeepsTheLocalesItWasAskedForAndTheirAncestors") {
        val bundle = bundleFile(rootDir).bufferedReader().use(LocaleDataBundle::readFrom)
        val narrowed = bundle.narrowTo(setOf("pt-BR", "es-AR", "en"))

        assertTrue("pt-BR" in narrowed.countryNames, "the locale asked for")
        // A sparse record points at its parent for everything it does not
        // declare, so dropping the parent would resolve almost nothing.
        assertTrue("pt" in narrowed.countryNames, "pt-BR inherits from pt")
        // es-AR's CLDR parent is es-419, not es, and the chain has to follow
        // that rather than plain truncation.
        assertTrue("es-419" in narrowed.countryNames, "es-AR inherits from es-419")
        assertTrue("es" in narrowed.countryNames, "es-419 inherits from es")
        assertTrue("root" in narrowed.countryNames, "root is always kept")
        assertTrue("ja" !in narrowed.countryNames, "an unrelated locale is dropped")

        assertTrue(
            narrowed.countryNames.size < bundle.countryNames.size / 50,
            "narrowing to three locales kept ${narrowed.countryNames.size} of ${bundle.countryNames.size}",
        )
        // Entity data is not locale data, so narrowing locales keeps all of it.
        assertEquals(bundle.countries.size, narrowed.countries.size)
        assertEquals(bundle.currencies.size, narrowed.currencies.size)
    }

    test("narrowingEntitiesKeepsTheEntriesAskedForAndTheRowsThatCanReachThem") {
        val bundle = bundleFile(rootDir).bufferedReader().use(LocaleDataBundle::readFrom)
        val narrowed = bundle.narrowEntitiesTo(countries = setOf("BR", "US"), currencies = setOf("BRL", "USD"))

        assertEquals(listOf("BR", "US"), narrowed.countries.map(CountryInfo::alpha2))
        assertEquals(listOf("BRL", "USD"), narrowed.currencies.map(CurrencyEntry::code))

        // The other axis is untouched: this is a build that named two countries,
        // not one that named two languages.
        assertEquals(bundle.localeTags.size, narrowed.localeTags.size)
        assertEquals(bundle.countryNames.keys, narrowed.countryNames.keys)

        // The names go with the entry set, because a name for a country the enum
        // no longer has is a row nothing can look up. This is the larger half of
        // what narrowing the enum is for: the enum itself is 12 KB of source and
        // the territory tables across every locale are hundreds.
        val portuguese = narrowed.countryNames.getValue("pt")
        assertTrue("Brasil" in portuguese, "the name of a country that was kept")
        assertFalse("Alemanha" in portuguese, "the name of a country that was dropped")
        assertTrue(
            narrowed.countryNames.values.sumOf(String::length) < bundle.countryNames.values.sumOf(String::length) / 20,
            "keeping two of 249 countries should drop almost every name",
        )

        // A row per country that has one of the kept currencies, and only those
        // codes in it. A country whose codes were all dropped goes entirely,
        // which is what makes Country.currency answer null rather than reach for
        // an entry the enum does not have.
        assertEquals("BRL", narrowed.countryCurrencies["BR"])
        assertEquals(null, narrowed.countryCurrencies["JP"], "a country left with no kept currency")

        // The field layout has to survive, or every lookup past the narrowed one
        // reads the wrong field. currencyNames carries two.
        for ((tag, record) in narrowed.currencyNames) {
            assertEquals(3, record.split(FIELD_SEPARATOR).size, "$tag lost a field")
        }
    }

    test("narrowingEntitiesRefusesACodeTheBundleDoesNotCarry") {
        val bundle = bundleFile(rootDir).bufferedReader().use(LocaleDataBundle::readFrom)
        val failure = assertFailsWith<IllegalArgumentException> {
            bundle.narrowEntitiesTo(countries = setOf("BR", "ZZ"))
        }
        assertTrue("no country data for ZZ" in failure.message.orEmpty(), "got: ${failure.message}")
    }
}
