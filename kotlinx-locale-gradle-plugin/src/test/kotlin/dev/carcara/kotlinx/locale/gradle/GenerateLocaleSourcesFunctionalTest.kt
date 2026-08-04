package dev.carcara.kotlinx.locale.gradle

import dev.carcara.kotlinx.locale.codegen.emittedFilePrefixes
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
 */
class GenerateLocaleSourcesFunctionalTest {

    private lateinit var projectDir: File

    private val bundle: String = System.getProperty("kotlinx.locale.bundle")
        ?: error("kotlinx.locale.bundle is not set; see gradle-plugin/build.gradle.kts")

    @BeforeTest
    fun setUp() {
        projectDir = File.createTempFile("kotlinx-locale-plugin", "").apply {
            delete()
            mkdirs()
        }
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"consumer\"\n")
    }

    @AfterTest
    fun tearDown() {
        projectDir.deleteRecursively()
    }

    private fun buildFile(
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
                kotlinxLocaleCldrData(files("${bundle.replace("\\", "\\\\")}"))
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

    private fun run(vararg arguments: String): BuildResult = runner(*arguments).build()

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
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
    private fun isolatedRunner(testKitDir: File, vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withTestKitDir(testKitDir)
        .withArguments(*arguments, "--stacktrace")
        .forwardOutput()

    private fun generated(path: String) = File(projectDir, "build/generated/kotlinx-locale/$path")

    @Test
    fun `generates a narrowed source set`() {
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

    @Test
    fun `only generates the features that were asked for`() {
        buildFile(features = "datetime { patterns = true }")
        run("generateLocaleSources")

        assertTrue(generated("com/example/locale/LocalizedFormat.kt").isFile, "datetime was requested")
        assertFalse(generated("com/example/locale/CountryNames.kt").isFile, "country was not requested")
        assertFalse(generated("com/example/locale/CurrencyNames.kt").isFile, "currency was not requested")
        assertFalse(generated("com/example/locale/SkeletonFormat.kt").isFile, "skeletons were not requested")
    }

    @Test
    fun `skeletons pull in the patterns they are matched against`() {
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

    @Test
    fun `currency formats pull in the symbols their patterns substitute`() {
        buildFile(features = "currency { formats = true }")
        run("generateLocaleSources")

        val tables = generated("com/example/locale/internal/data").list()?.toList().orEmpty()
        assertTrue(tables.any { it.startsWith("CurrencyFormats") }, "the patterns are missing")
        // A pattern contains a currency placeholder, so without the symbol table
        // it would render a hole.
        assertTrue(tables.any { it.startsWith("CurrencyNames") }, "the symbols the patterns need are missing")
    }

    @Test
    fun `every feature generates the whole closure of tables it declared`() {
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
        val outerProjectDir = projectDir
        for (feature in LocaleFeature.entries) {
            setUp()
            try {
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
            } finally {
                tearDown()
            }
        }
        // The directory the lifecycle made for this method, which the loop
        // replaced on its first pass and so never cleaned up.
        outerProjectDir.deleteRecursively()
        testKitDir.deleteRecursively()
    }

    @Test
    fun `a narrowed build gets the same entry points as the bundled artifacts`() {
        // The property that matters to a reader of API.md: an entry point is not
        // a property of the -cldr-full artifact, it is a property of the emitter,
        // and the plugin calls the same one. If these drift, the documentation
        // sends narrowed builds looking for functions they do not have.
        buildFile(
            features = "datetime { patterns = true; skeletons = true; intervals = true; durationUnits = true }\n" +
                "personName { formats = true }",
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

        val names = generated("com/example/locale/PersonNameFormat.kt").readText()
        assertContains(names, "public fun personNameFormat(")
        assertContains(names, "public fun personNameOrder(")
    }

    @Test
    fun `is up to date on a second run and reruns when the locale set changes`() {
        buildFile()
        assertEquals(TaskOutcome.SUCCESS, run("generateLocaleSources").task(":generateLocaleSources")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, run("generateLocaleSources").task(":generateLocaleSources")?.outcome)

        buildFile(locales = """locales("pt-BR", "en", "ja")""")
        assertEquals(TaskOutcome.SUCCESS, run("generateLocaleSources").task(":generateLocaleSources")?.outcome)
    }

    @Test
    fun `reuses the configuration cache`() {
        buildFile()
        val first = run("generateLocaleSources", "--configuration-cache")
        assertContains(first.output, "Configuration cache entry stored")

        val second = run("generateLocaleSources", "--configuration-cache")
        assertContains(second.output, "Configuration cache entry reused")
    }

    @Test
    fun `loads from the build cache after its output is deleted`() {
        buildFile()
        run("generateLocaleSources", "--build-cache")

        // Deleting the declared output is what distinguishes a cache hit from an
        // up-to-date check; without this the second run proves nothing.
        File(projectDir, "build/generated/kotlinx-locale").deleteRecursively()

        val result = run("generateLocaleSources", "--build-cache")
        assertEquals(TaskOutcome.FROM_CACHE, result.task(":generateLocaleSources")?.outcome)
        assertTrue(generated("com/example/locale/CountryNames.kt").isFile, "the cached output was not restored")
    }

    @Test
    fun `stops generating a feature that was turned off`() {
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

    @Test
    fun `refuses a fallback that is not one of the generated locales`() {
        buildFile(fallback = """fallback("ja")""")
        val failure = runner("generateLocaleSources").buildAndFail()
        assertContains(failure.output, "the fallback locale 'ja' is not one of the generated locales")
    }

    @Test
    fun `refuses a locale CLDR has no data for`() {
        buildFile(locales = """locales("pt-BR", "en", "zz-ZZ")""")
        val failure = runner("generateLocaleSources").buildAndFail()
        assertContains(failure.output, "no CLDR data for locale 'zz-ZZ'")
    }

    @Test
    fun `refuses a configuration that would generate nothing`() {
        buildFile(features = "")
        val failure = runner("generateLocaleSources").buildAndFail()
        assertContains(failure.output, "kotlinxLocale generates nothing")
    }
}
