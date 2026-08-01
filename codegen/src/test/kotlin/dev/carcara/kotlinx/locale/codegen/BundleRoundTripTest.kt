package dev.carcara.kotlinx.locale.codegen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
class BundleRoundTripTest {

    private val rootDir = File(
        System.getProperty("kotlinx.locale.rootDir") ?: error("kotlinx.locale.rootDir is not set"),
    )

    @Test
    fun theBundleRegeneratesEveryShippedSourceByteForByte() {
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

    @Test
    fun narrowingKeepsTheLocalesItWasAskedForAndTheirAncestors() {
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

    private fun createTempDirectory(): File = File.createTempFile("kotlinx-locale-roundtrip", "").let { file ->
        file.delete()
        file.mkdirs()
        file
    }
}
