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

package dev.carcara.kotlinx.locale.gradle

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.codegen.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.codegen.LIST_SEPARATOR
import dev.carcara.kotlinx.locale.codegen.emittedFilePrefixes
import dev.carcara.kotlinx.locale.codegen.kotlinUnescape
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.PayloadDateTimeFormats
import dev.carcara.kotlinx.locale.datetime.format
import dev.carcara.kotlinx.locale.test.assertContains
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertFalse
import dev.carcara.kotlinx.locale.test.assertNotNull
import dev.carcara.kotlinx.locale.test.assertTrue
import kotlinx.datetime.LocalTime
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

/**
 * Runs the plugin in a real build.
 *
 * The unit tests cover what the generator writes; these cover what Gradle does
 * with it, which is the part that breaks in ways no unit test sees: a task that
 * never goes up to date, a cache entry that never hits, a configuration cache
 * entry that cannot be reused because something captured the wrong thing.
 *
 * The bundle comes from the checked-in resource rather than from Maven, so these
 * tests need no publishing step and no network.
 *
 * The temporary project used to be a `lateinit var` under `@BeforeTest` and
 * `@AfterTest`. It is a scope function now, because registration and execution
 * are separate phases here: a field assigned during registration is one field
 * shared by every test, and the feature sweep below already had to call the
 * lifecycle by hand to work around that. [withProject] makes the lifetime the
 * block rather than the instance, which is what it always meant.
 */
private val BUNDLE: String = System.getProperty("kotlinx.locale.bundle")
    ?: error("kotlinx.locale.bundle is not set; see gradle-plugin/build.gradle.kts")

private class ConsumerProject(val projectDir: File) {

    fun buildFile(
        locales: String = """locales("pt-BR", "en")""",
        fallback: String = """fallback("en")""",
        features: String = "country { names = true }",
        packageName: String = "com.example.locale",
    ) {
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("dev.carcara.kotlinx-locale")
            }

            dependencies {
                kotlinxLocaleCldrData(files("${BUNDLE.replace("\\", "\\\\")}"))
            }

            kotlinxLocale {
                $locales
                $fallback
                packageName = "$packageName"
                $features
            }
            """.trimIndent(),
        )
    }

    fun run(vararg arguments: String): BuildResult = runner(*arguments).build()

    fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")
        .forwardOutput()

    /**
     * A runner with its own TestKit directory.
     *
     * The default one is shared by every `GradleRunner` in the JVM, and so is the
     * daemon and the file-watching state behind it. That is fine for tests that
     * run one or two builds, and not fine for the feature sweep below, which runs
     * one per feature: the tests that assert on up-to-date and cache outcomes
     * depend on that state being quiet, and sixteen builds churning it is how a
     * deleted output goes unnoticed and a cache hit reads as up to date.
     */
    fun isolatedRunner(testKitDir: File, vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withTestKitDir(testKitDir)
        .withArguments(*arguments, "--stacktrace")
        .forwardOutput()

    fun generated(path: String) = File(projectDir, "build/generated/kotlinx-locale/$path")
}

/** Runs [block] against a fresh consumer project, and deletes it afterwards. */
private fun <T> withProject(block: ConsumerProject.() -> T): T {
    val dir = File.createTempFile("kotlinx-locale-plugin", "").apply {
        delete()
        mkdirs()
    }
    File(dir, "settings.gradle.kts").writeText("rootProject.name = \"consumer\"\n")
    return try {
        ConsumerProject(dir).block()
    } finally {
        dir.deleteRecursively()
    }
}

/**
 * The DSL block that turns this feature on.
 *
 * `country.names` becomes `country { names = true }`, which is what a user
 * would type, so a failure quotes the configuration rather than the enum.
 */
private fun LocaleFeature.asBuildScript(): String {
    val block = dslName.substringBefore('.')
    val property = dslName.substringAfter('.')
    return "$block { $property = true }"
}

/** The fields a pattern record carries once the hour cycle is on it. */
private const val RECORD_FIELDS = 28

/** The locale's own SHORT time pattern. */
private const val SHORT_TIME_FIELD = 17

/** The `<timeData>` row: the preferred hour letter, then the allowed entries in preference order. */
private const val HOUR_CYCLE_FIELD = 26

/** The other hour family's time patterns, FULL to SHORT. */
private const val ALTERNATE_TIME_FIELD = 27

/** One record's fields, in the order the generator wrote them. */
private fun String.fields(): List<String> = split(FIELD_SEPARATOR)

/** The list items of one field. */
private fun String.entries(field: Int): List<String> = fields()[field].split(LIST_SEPARATOR)

private val PAYLOAD_CONSTANT = Regex("""internal val (\w+): String =\n\s+"(.*)"\n""")

private val REGISTRY_ENTRY = Regex("""put\("([^"]+)", (\w+)\)""")

/**
 * The pattern table this build generated, read back out of the Kotlin it wrote.
 *
 * The emitter deduplicates records into constants bucketed by the first letter
 * of the tag that owns them and leaves the registry saying which constant
 * answers for which tag, so reading a field means putting those halves back
 * together and undoing the escaping. What comes out is the map the generated
 * binding hands the runtime.
 */
private fun ConsumerProject.localeDataTable(): Map<String, String> {
    val dataDir = generated("com/example/locale/internal/data")
    val constants = dataDir.listFiles().orEmpty()
        .filter { it.name.startsWith("LocaleData_") }
        .flatMap { PAYLOAD_CONSTANT.findAll(it.readText()) }
        .associate { it.groupValues[1] to kotlinUnescape(it.groupValues[2]) }
    val registry = dataDir.resolve("LocaleDataRegistry.kt")
    assertTrue(registry.isFile, "the pattern table was not generated")
    return REGISTRY_ENTRY.findAll(registry.readText())
        .associate { it.groupValues[1] to constants.getValue(it.groupValues[2]) }
}

val GenerateLocaleSourcesFunctionalTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    test("generates a narrowed source set") {
        withProject {
            buildFile()
            val result = run("generateLocaleSources")

            assertEquals(TaskOutcome.SUCCESS, result.task(":generateLocaleSources")?.outcome)

            val binding = generated("com/example/locale/CountryNames.kt")
            assertTrue(binding.isFile, "the source object was not generated")
            val text = binding.readText()
            assertContains(text, "public object GeneratedCountryNames")
            assertContains(text, "import com.example.locale.internal.data.countryNamesRegistry")
            // The convenience extension is the promise that call sites do not move.
            assertContains(text, "public fun Country.displayName(locale: Locale = Locale.current): String")

            val registry = generated("com/example/locale/internal/data/CountryNamesRegistry.kt")
            assertTrue(registry.isFile, "the table was not generated")
            val tables = registry.readText()
            assertContains(tables, "put(\"pt-BR\"")
            assertContains(tables, "put(\"pt\"", ignoreCase = false)
            // Root is the fallback locale's flattened record, which is what keeps an
            // unlisted locale answering.
            assertContains(tables, "put(\"root\"")
            assertFalse("put(\"ja\"" in tables, "an unrelated locale was kept")
        }
    }

    test("only generates the features that were asked for") {
        withProject {
            buildFile(features = "datetime { patterns = true }")
            run("generateLocaleSources")

            assertTrue(generated("com/example/locale/LocalizedFormat.kt").isFile, "datetime was requested")
            assertFalse(generated("com/example/locale/CountryNames.kt").isFile, "country was not requested")
            assertFalse(generated("com/example/locale/CurrencyNames.kt").isFile, "currency was not requested")
            assertFalse(generated("com/example/locale/SkeletonFormat.kt").isFile, "skeletons were not requested")
        }
    }

    test("skeletons pull in the patterns they are matched against") {
        withProject {
            buildFile(features = "datetime { skeletons = true }")
            run("generateLocaleSources")

            // The matcher scores a request against the locale's standard date and
            // time patterns as well as its skeleton table, and renders the winner
            // with its month and weekday names, so asking for skeletons alone still
            // has to produce both tables and both bindings.
            val binding = generated("com/example/locale/SkeletonFormat.kt")
            assertTrue(binding.isFile, "skeletons were requested")
            assertContains(binding.readText(), "GeneratedDateTimeSkeletons")
            assertContains(binding.readText(), "GeneratedDateTime.records")
            assertTrue(generated("com/example/locale/LocalizedFormat.kt").isFile, "skeletons imply patterns")

            val tables = generated("com/example/locale/internal/data").list()?.toList().orEmpty()
            assertTrue(tables.any { it.startsWith("SkeletonFormats") }, "the skeleton table is missing")
            assertTrue(tables.any { it.startsWith("SkeletonAppendFormats") }, "the append formats are missing")
            assertTrue(tables.any { it.startsWith("SkeletonNames") }, "the names and quarters are missing")
            assertTrue(tables.any { it.startsWith("LocaleData") }, "the patterns to match against are missing")
        }
    }

    test("the pattern table carries the hour cycle row CLDR gives each locale") {
        withProject {
            buildFile(locales = """locales("de", "en")""", features = "datetime { patterns = true }")
            run("generateLocaleSources")

            val table = localeDataTable()
            assertTrue(table.isNotEmpty(), "the pattern table is empty")
            for ((tag, record) in table) {
                assertEquals(RECORD_FIELDS, record.fields().size, "$tag's record stops short of the hour cycle fields")
            }

            // supplementalData.xml gives DE `preferred="H" allowed="H hB"` and US
            // `preferred="h" allowed="h hb H hB"`. A row that kept only its head
            // would still decode, and would answer c12 with the locale's own cycle.
            assertEquals(listOf("H", "H", "hB"), assertNotNull(table["de"]).entries(HOUR_CYCLE_FIELD))
            assertEquals(listOf("h", "h", "hb", "H", "hB"), assertNotNull(table["en"]).entries(HOUR_CYCLE_FIELD))
        }
    }

    test("a narrowed German build carries the twelve-hour patterns German never writes") {
        withProject {
            buildFile(locales = """locales("de", "en")""", features = "datetime { patterns = true }")
            run("generateLocaleSources")

            val table = localeDataTable()
            val german = assertNotNull(table["de"], "the locale that was asked for is missing from the table")
            assertEquals("HH:mm", german.fields()[SHORT_TIME_FIELD], "de's own SHORT time left the 24-hour clock")
            // de.xml declares no twelve-hour time pattern of its own, so these are
            // its hm and hms skeletons carrying the zone decoration of its own FULL
            // and LONG patterns. The day period is separated by U+202F.
            assertEquals(
                listOf("h:mm:ss\u202Fa zzzz", "h:mm:ss\u202Fa z", "h:mm:ss\u202Fa", "h:mm\u202Fa"),
                german.entries(ALTERNATE_TIME_FIELD),
            )
            assertEquals(
                listOf("HH:mm:ss zzzz", "HH:mm:ss z", "HH:mm:ss", "HH:mm"),
                assertNotNull(table["en"]).entries(ALTERNATE_TIME_FIELD),
            )
        }
    }

    test("a narrowed build renders the clock the locale asks for") {
        withProject {
            buildFile(locales = """locales("de", "en")""", features = "datetime { patterns = true }")
            run("generateLocaleSources")

            // The same class the generated binding constructs, over the same table
            // it would hand it.
            val formats = PayloadDateTimeFormats(localeDataTable())
            val time = LocalTime(15, 30)
            assertEquals("15:30", formats.format(time, FormatStyle.SHORT, Locale.forLanguageTag("de")))
            assertEquals("3:30\u202FPM", formats.format(time, FormatStyle.SHORT, Locale.forLanguageTag("de-u-hc-h12")))
            assertEquals("15:30", formats.format(time, FormatStyle.SHORT, Locale.forLanguageTag("en-u-hc-h23")))
        }
    }

    test("currency formats pull in the symbols their patterns substitute") {
        withProject {
            buildFile(features = "currency { formats = true }")
            run("generateLocaleSources")

            val tables = generated("com/example/locale/internal/data").list()?.toList().orEmpty()
            assertTrue(tables.any { it.startsWith("CurrencyFormats") }, "the patterns are missing")
            // A pattern contains a currency placeholder, so without the symbol table
            // it would render a hole.
            assertTrue(tables.any { it.startsWith("CurrencyNames") }, "the symbols the patterns need are missing")
        }
    }

    test("currency symbols without the patterns carry no formatter") {
        withProject {
            buildFile(features = "currency { names = true }")
            run("generateLocaleSources")

            val binding = generated("com/example/locale/CurrencyNames.kt").readText()
            assertContains(binding, "public fun Currency.symbol(")
            assertContains(binding, "public fun Currency.displayName(")
            // The patterns were not asked for, so there is nothing to format with.
            // An absent entry point makes the call site fail to compile, which is
            // the right failure; a formatter over a table nobody wrote is not.
            assertFalse(
                "public fun CurrencyAmount.format(" in binding,
                "the format entry point was generated without the pattern table behind it",
            )
            assertFalse(
                "currencyFormatsRegistry" in binding,
                "the binding refers to a registry currency.names does not generate",
            )
        }
    }

    test("every feature generates the whole closure of tables it declared") {
        // The plugin's correctness property in one test. A feature declares the
        // set of tables it needs rather than pointing at other features, so the
        // thing to check is that asking for one produces all of them: the
        // guarantee is that no configuration compiles and then answers wrongly,
        // and a half-populated source set is exactly how that would happen.
        //
        // Driven off the enum and off the emitter's own file names, so a feature
        // added later is covered without editing this test, and a table renamed
        // later cannot pass by matching a stale string.
        val testKitDir = File.createTempFile("kotlinx-locale-features", "").apply {
            delete()
            mkdirs()
        }
        try {
            for (feature in LocaleFeature.entries) {
                withProject {
                    buildFile(features = feature.asBuildScript())
                    val result = isolatedRunner(testKitDir, "generateLocaleSources").build()
                    assertEquals(
                        TaskOutcome.SUCCESS,
                        result.task(":generateLocaleSources")?.outcome,
                        "${feature.dslName} did not generate",
                    )

                    val emitted = generated("com/example/locale/internal/data").list()?.toList().orEmpty()
                    for (table in feature.tables) {
                        for (prefix in table.emittedFilePrefixes) {
                            assertTrue(
                                emitted.any { it.startsWith(prefix) },
                                "${feature.dslName} declares $table but no $prefix file was written; got $emitted",
                            )
                        }
                    }
                }
            }
        } finally {
            testKitDir.deleteRecursively()
        }
    }

    test("a narrowed build gets the same entry points as the bundled artifacts") {
        withProject {
            // The property that matters to a reader of API.md: an entry point is not
            // a property of the -cldr-full artifact, it is a property of the emitter,
            // and the plugin calls the same one. If these drift, the documentation
            // sends narrowed builds looking for functions they do not have.
            buildFile(
                features = "datetime { patterns = true; skeletons = true; intervals = true; durationUnits = true }\n" +
                    "personName { formats = true }\n" +
                    "currency { pluralNames = true }",
            )
            run("generateLocaleSources")

            val dateTime = generated("com/example/locale/LocalizedFormat.kt").readText()
            assertContains(dateTime, "public fun durationPattern(")
            assertContains(dateTime, "public fun weekInfo(")
            assertContains(dateTime, "public fun weekInfoForRegion(")

            val intervals = generated("com/example/locale/IntervalFormat.kt").readText()
            assertContains(intervals, "public fun intervalFormat(")

            // The measurement form, which is a different file and a different table
            // from the durationPattern above. Both names start with `duration`, so
            // asserting one and assuming the other is exactly the drift this catches.
            val durations = generated("com/example/locale/DurationUnits.kt").readText()
            assertContains(durations, "public fun durationFormat(")
            assertContains(durations, "public fun durationUnitName(")

            // The name form, which is a third artifact and a third table. It is the
            // one entry point in the library that a narrowed build reaches through a
            // different object from the bundled one, so it is worth naming here.
            val plurals = generated("com/example/locale/CurrencyPlurals.kt").readText()
            assertContains(plurals, "public fun CurrencyAmount.formatPluralName(")
            assertContains(plurals, "public fun Currency.pluralName(")

            val names = generated("com/example/locale/PersonNameFormat.kt").readText()
            assertContains(names, "public fun personNameFormat(")
            assertContains(names, "public fun personNameOrder(")
        }
    }

    test("is up to date on a second run and reruns when the locale set changes") {
        withProject {
            buildFile()
            assertEquals(TaskOutcome.SUCCESS, run("generateLocaleSources").task(":generateLocaleSources")?.outcome)
            assertEquals(TaskOutcome.UP_TO_DATE, run("generateLocaleSources").task(":generateLocaleSources")?.outcome)

            buildFile(locales = """locales("pt-BR", "en", "ja")""")
            assertEquals(TaskOutcome.SUCCESS, run("generateLocaleSources").task(":generateLocaleSources")?.outcome)
        }
    }

    test("reuses the configuration cache") {
        withProject {
            buildFile()
            val first = run("generateLocaleSources", "--configuration-cache")
            assertContains(first.output, "Configuration cache entry stored")

            val second = run("generateLocaleSources", "--configuration-cache")
            assertContains(second.output, "Configuration cache entry reused")
        }
    }

    test("loads from the build cache after its output is deleted") {
        withProject {
            buildFile()
            run("generateLocaleSources", "--build-cache")

            // Deleting the declared output is what distinguishes a cache hit from an
            // up-to-date check; without this the second run proves nothing.
            File(projectDir, "build/generated/kotlinx-locale").deleteRecursively()

            val result = run("generateLocaleSources", "--build-cache")
            assertEquals(TaskOutcome.FROM_CACHE, result.task(":generateLocaleSources")?.outcome)
            assertTrue(generated("com/example/locale/CountryNames.kt").isFile, "the cached output was not restored")
        }
    }

    test("stops generating a feature that was turned off") {
        withProject {
            buildFile(features = "country { names = true }\ndatetime { patterns = true }")
            run("generateLocaleSources")
            assertTrue(generated("com/example/locale/LocalizedFormat.kt").isFile)

            buildFile(features = "country { names = true }")
            run("generateLocaleSources")
            assertFalse(
                generated("com/example/locale/LocalizedFormat.kt").isFile,
                "a source file for a disabled feature survived and would still compile",
            )
        }
    }

    test("refuses a fallback that is not one of the generated locales") {
        withProject {
            buildFile(fallback = """fallback("ja")""")
            val failure = runner("generateLocaleSources").buildAndFail()
            assertContains(failure.output, "the fallback locale 'ja' is not one of the generated locales")
        }
    }

    test("refuses a locale CLDR has no data for") {
        withProject {
            buildFile(locales = """locales("pt-BR", "en", "zz-ZZ")""")
            val failure = runner("generateLocaleSources").buildAndFail()
            assertContains(failure.output, "no CLDR data for locale 'zz-ZZ'")
        }
    }

    test("refuses a configuration that would generate nothing") {
        withProject {
            buildFile(features = "")
            val failure = runner("generateLocaleSources").buildAndFail()
            assertContains(failure.output, "kotlinxLocale generates nothing")
        }
    }

    test("generates the catalog for the declared locales and nothing else") {
        withProject {
            buildFile(locales = """locales("pt-BR", "en")""", features = "catalog = true")
            run("generateLocaleSources")

            // The catalog's own package, under the consumer's, because nothing in
            // the library declares an extension on a catalog enum and so nothing
            // pins where it lives.
            val portuguese = generated("com/example/locale/catalog/PT.kt")
            assertTrue(portuguese.isFile, "the language of a declared locale is missing")
            val text = portuguese.readText()
            assertContains(text, "package com.example.locale.catalog")
            assertContains(text, "public enum class PT(override val tag: String) : LocaleRef")
            assertContains(text, """BR("pt-BR")""")

            // narrowTo keeps the ancestors a sparse record resolves through, so
            // `en` is here as itself rather than as somebody's parent.
            assertTrue(generated("com/example/locale/catalog/EN.kt").isFile, "the other declared locale is missing")
            assertFalse(
                generated("com/example/locale/catalog/JA.kt").isFile,
                "a language nothing declared can be named, and would answer in the fallback",
            )
        }
    }

    test("narrows the Country enum to the entries that were named") {
        withProject {
            buildFile(features = """country { entries("BR", "US"); names = true }""")
            run("generateLocaleSources")

            // The library's own package, not the consumer's: kotlinx-locale-country-core
            // declares Country.alpha2 and Country.Companion.forAlpha2 on this
            // exact name, and an enum anywhere else would not be the one they
            // extend.
            val country = generated("dev/carcara/kotlinx/locale/country/Country.kt").readText()
            assertContains(country, "package dev.carcara.kotlinx.locale.country")
            assertContains(country, """BR("BRA", 76)""")
            assertContains(country, """US("USA", 840)""")
            assertFalse("""DE("DEU"""" in country, "a country nothing asked for survived")
            // The rule the whole library holds to, and the one a generated type
            // is most likely to drop.
            assertContains(country, "public companion object")

            // The names go with the entry set. A territory name for a country the
            // enum no longer has is a row no call site can reach.
            val names = generated("com/example/locale/internal/data").listFiles()
                .orEmpty()
                .filter { it.name.startsWith("CountryNames") }
                .joinToString("\n") { it.readText() }
            assertTrue(names.isNotEmpty(), "the name table is missing")
            assertContains(names, "Brasil")
            assertFalse("Alemanha" in names, "the name of a dropped country survived")
        }
    }

    test("narrowing the currencies takes the country-to-currency map with them") {
        withProject {
            buildFile(features = """currency { entries("BRL", "USD") }""")
            run("generateLocaleSources")

            val currency = generated("dev/carcara/kotlinx/locale/currency/Currency.kt").readText()
            assertContains(currency, "BRL(986,")
            assertFalse("JPY(392," in currency, "a currency nothing asked for survived")

            // The tender windows are read by Currency ordinal, so the row count
            // has to follow the entry count exactly. Two entries, two rows. A
            // table that kept all three hundred would answer `isActive` from
            // whatever currency used to sit at ordinal 0.
            val tender = generated("dev/carcara/kotlinx/locale/currency/internal/CurrencyTender.kt")
                .readLines()
                .last { it.trimStart().startsWith("\"") }
                .trim()
                .removeSurrounding("\"")
            assertEquals(2, tender.split(';').size, "the tender table and the enum disagree about how many entries there are")

            // Ships in the same artifact the exclusion removes, so generating the
            // enum has to generate this too or Country.currency reads a table
            // nothing declares.
            val map = generated("dev/carcara/kotlinx/locale/currency/internal/CountryCurrencies.kt").readText()
            assertContains(map, "BR")
            assertContains(map, "BRL")
            assertFalse("JPY" in map, "a dropped currency survived in the country map")
        }
    }

    test("excludes the published artifact of every type it generates, and only those") {
        withProject {
            // The exclusion is what makes the generated enum resolvable at all:
            // the -core module carries the shipped one as an api dependency, so
            // without this a build gets two Country classes on one classpath and
            // whichever the compiler saw first.
            File(projectDir, "build.gradle.kts").writeText(
                """
                plugins {
                    id("dev.carcara.kotlinx-locale")
                }

                val probe by configurations.creating

                dependencies {
                    kotlinxLocaleCldrData(files("${BUNDLE.replace("\\", "\\\\")}"))
                }

                kotlinxLocale {
                    locales("pt-BR", "en")
                    fallback("en")
                    packageName = "com.example.locale"
                    catalog = true
                    country { entries("BR", "US") }
                }

                tasks.register("printExcludes") {
                    val rules = provider {
                        probe.excludeRules.map { it.group + ":" + it.module }.sorted()
                    }
                    doLast { println("excludes=" + rules.get()) }
                }
                """.trimIndent(),
            )
            // Twice with the configuration cache, because the exclusion is
            // applied from afterEvaluate and a cache hit skips configuration
            // altogether. A rule that only existed on the storing run would let
            // the shipped enum back in on every later build.
            run("printExcludes", "--configuration-cache")
            val output = run("printExcludes", "--configuration-cache").output
            assertContains(output, "Configuration cache entry reused")

            assertContains(output, "dev.carcara:kotlinx-locale-country-types")
            // The catalog replaces nothing. kotlinx-locale-types is a leaf, so a
            // build that generates its own simply leaves the dependency out, and
            // excluding it would break a build that wanted both.
            assertFalse(
                "kotlinx-locale-types" in output.substringAfter("excludes=").substringBefore("\n")
                    .replace("kotlinx-locale-country-types", ""),
                "the catalog is not a replacement and must not be excluded",
            )
            // Not asked for, so the shipped Currency stays.
            assertFalse(
                "kotlinx-locale-currency-types" in output,
                "a type nothing narrowed had its artifact excluded",
            )
        }
    }

    test("a build that only asks for a type still generates") {
        withProject {
            // The "generates nothing" check counts types as well as features, so
            // a catalog-only build is a configuration rather than a failure. This
            // is the case the whole thing was asked for: a project that wants the
            // locale list and none of the data.
            buildFile(features = "catalog = true")
            assertEquals(TaskOutcome.SUCCESS, run("generateLocaleSources").task(":generateLocaleSources")?.outcome)
            assertTrue(generated("com/example/locale/catalog/EN.kt").isFile)
            assertFalse(generated("com/example/locale/internal/data").isDirectory, "no data was asked for")
        }
    }

    test("refuses an entry the bundle has no data for") {
        withProject {
            buildFile(features = """country { entries("BR", "ZZ") }""")
            val failure = runner("generateLocaleSources").buildAndFail()
            assertContains(failure.output, "no country data for ZZ")
        }
    }
}
