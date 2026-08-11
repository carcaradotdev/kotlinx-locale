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
import dev.carcara.kotlinx.locale.codegen.BindingTarget
import dev.carcara.kotlinx.locale.codegen.LocaleDataBundle
import dev.carcara.kotlinx.locale.codegen.RegistryPackages
import dev.carcara.kotlinx.locale.codegen.SourceRoots
import dev.carcara.kotlinx.locale.codegen.generateSources
import dev.carcara.kotlinx.locale.test.assertEquals
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

/**
 * Compiles what each feature generates, one feature at a time.
 *
 * The other tests check that the declared tables were written and that the entry
 * points came out named the way the documentation says. Neither notices a source
 * file that refers to something nobody generated, because both read the output
 * as text. Only a compiler reads it as Kotlin.
 *
 * One feature per compilation, not all of them at once. Enabling everything
 * unions the tables and hides exactly the bug this exists for: `timezone
 * { formats = true }` emitted a number binding that reached for the plural
 * registry, and any configuration that also asked for a number flag wrote that
 * registry and made the mistake invisible.
 *
 * The classpath is this test's own. The runtime modules are declared as test
 * dependencies in `build.gradle.kts`, which is the same set a narrowed consumer
 * depends on in place of `-cldr-full`.
 */
/**
 * Not the library's own package. A generated source set is meant to sit beside
 * the shipped artifacts rather than inside them, and compiling it under a
 * package the classpath already carries would let a shipped declaration answer
 * for one the generator forgot to write.
 */
private const val PACKAGE = "com.example.locale"

val GeneratedSourceCompilesTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {
    val bundlePath: String = System.getProperty("kotlinx.locale.bundle")
        ?: error("kotlinx.locale.bundle is not set; see kotlinx-locale-gradle-plugin/build.gradle.kts")

    /**
     * Two locales and a fallback, which is enough for every table to be
     * non-empty. The point here is the shape of the generated code, and that
     * does not vary with the locale count.
     */
    val bundle: LocaleDataBundle by lazy {
        File(bundlePath).bufferedReader().use(LocaleDataBundle::readFrom)
            .narrowTo(tags = setOf("pt-BR", "en"), fallbackTag = "en")
    }

    fun generate(feature: LocaleFeature, root: File) {
        val builder = SourceRoots.Builder()
        for (table in feature.tables) builder.table(table, root)
        for (binding in feature.bindings) {
            builder.binding(
                binding,
                BindingTarget(root = root, packageName = PACKAGE, objectName = "Generated" + binding.objectSuffix),
            )
        }
        generateSources(bundle = bundle, roots = builder.build(), packages = RegistryPackages.under(PACKAGE))
    }

    /**
     * One type's tables, against a bundle narrowed on both axes.
     *
     * Two entries per entity rather than the whole set, because what is being
     * compiled here is the shape of a narrowed enum: that it still declares its
     * companion, that the tender string still lines up with the entries it
     * indexes, and that the country-to-currency map only names entries the enum
     * has.
     */
    fun generate(type: LocaleType, root: File) {
        val builder = SourceRoots.Builder()
        for (table in type.tables) builder.table(table, root)
        generateSources(
            bundle = bundle.narrowEntitiesTo(countries = setOf("BR", "US"), currencies = setOf("BRL", "USD")),
            roots = builder.build(),
            packages = RegistryPackages.under(PACKAGE),
        )
    }

    /**
     * Null when it compiled, the compiler's own output when it did not.
     *
     * [without] drops a published module from the classpath, which is what the
     * plugin's own exclusion does in a consumer's build. A generated `Country`
     * has to take `dev.carcara.kotlinx.locale.country.Country` for the `-core`
     * extensions to resolve against it, so compiling one while the shipped
     * artifact is still there would prove nothing about the configuration a
     * consumer actually gets.
     */
    fun compile(sources: File, destination: File, without: List<String> = emptyList()): String? {
        val classpath = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .filter { entry -> without.none { entry.contains(it) } }
            .joinToString(File.pathSeparator)
        val output = ByteArrayOutputStream()
        val exitCode = PrintStream(output, true, Charsets.UTF_8).use { stream ->
            K2JVMCompiler().exec(
                stream,
                "-nowarn",
                "-no-stdlib", // Already on the classpath below; adding it twice is a duplicate-class error.
                "-jvm-target",
                "21",
                "-classpath",
                classpath,
                "-d",
                destination.absolutePath,
                sources.absolutePath,
            )
        }
        return if (exitCode.code == 0) null else output.toString(Charsets.UTF_8).trim()
    }

    fun createTempDir(prefix: String): File = File.createTempFile(prefix, "").apply {
        delete()
        mkdirs()
    }

    test("every feature generates source that compiles on its own") {
        val failures = LinkedHashMap<String, String>()
        for (feature in LocaleFeature.entries) {
            val workingDir = createTempDir("kotlinx-locale-compile-${feature.name}")
            try {
                val sources = workingDir.resolve("src").apply { mkdirs() }
                generate(feature, sources)
                val diagnostics = compile(sources, workingDir.resolve("classes"))
                if (diagnostics != null) failures[feature.dslName] = diagnostics
            } finally {
                workingDir.deleteRecursively()
            }
        }
        assertEquals(
            emptyMap(),
            failures,
            "a feature whose generated source does not compile is a configuration a consumer can " +
                "write and then fail to build. Add what it reads to that feature's table closure in " +
                "LocaleFeature, or make the emitter leave the entry point out.",
        )
    }

    /**
     * A call site for each type, written the way the documentation says it stays
     * written.
     *
     * The generated enum compiling on its own is not the claim. The claim is that
     * `kotlinx-locale-country-core`'s extensions resolve against it, which they
     * only do because it takes the library's own package and name. Nothing else
     * in this test would notice if a generated enum landed in the consumer's
     * package instead: it would compile, and every `Country.alpha2` in the
     * consumer's code would then fail to resolve.
     */
    fun callSite(type: LocaleType): String? = when (type) {
        LocaleType.LOCALE_CATALOG ->
            """
            package $PACKAGE.callsite
            import dev.carcara.kotlinx.locale.Locale
            import $PACKAGE.catalog.PT
            val tag: String = PT.BR.tag
            val locale: Locale = Locale.forLanguageTag(PT.BR.tag)
            """.trimIndent()
        LocaleType.COUNTRY_ENTRIES ->
            """
            package $PACKAGE.callsite
            import dev.carcara.kotlinx.locale.country.Country
            import dev.carcara.kotlinx.locale.country.alpha2
            import dev.carcara.kotlinx.locale.country.forAlpha2OrNull
            val code: String = Country.BR.alpha2
            val three: String = Country.BR.alpha3
            // Still total, and still answers null rather than throwing for a code
            // this build did not generate.
            val dropped: Country? = Country.forAlpha2OrNull("DE")
            """.trimIndent()
        LocaleType.CURRENCY_ENTRIES ->
            """
            package $PACKAGE.callsite
            import dev.carcara.kotlinx.locale.country.Country
            import dev.carcara.kotlinx.locale.currency.Currency
            import dev.carcara.kotlinx.locale.currency.currency
            import dev.carcara.kotlinx.locale.currency.forCodeOrNull
            val units: Int = Currency.BRL.defaultFractionDigits
            val dropped: Currency? = Currency.forCodeOrNull("JPY")
            // Reads the country-to-currency map this type also generates.
            val ofCountry: Currency? = Country.BR.currency
            """.trimIndent()
    }

    test("every type generates source the library's own extensions resolve against") {
        val failures = LinkedHashMap<String, String>()
        for (type in LocaleType.entries) {
            val workingDir = createTempDir("kotlinx-locale-callsite-${type.name}")
            try {
                val sources = workingDir.resolve("src").apply { mkdirs() }
                generate(type, sources)
                // Country.BR.currency needs a Country, so the currency case
                // generates that enum too and excludes both shipped artifacts.
                val also = if (type == LocaleType.CURRENCY_ENTRIES) LocaleType.COUNTRY_ENTRIES else null
                also?.let { generate(it, sources) }
                callSite(type)?.let { sources.resolve("CallSite.kt").writeText(it) }
                val diagnostics = compile(
                    sources,
                    workingDir.resolve("classes"),
                    without = listOfNotNull(type.replaces, also?.replaces),
                )
                if (diagnostics != null) failures[type.dslName] = diagnostics
            } finally {
                workingDir.deleteRecursively()
            }
        }
        assertEquals(
            emptyMap(),
            failures,
            "a generated type the library's extensions do not resolve against is one that landed in the " +
                "wrong package. Country and Currency have to keep dev.carcara.kotlinx.locale.*, because " +
                "that is where -core declares alpha2, forAlpha2 and the rest.",
        )
    }

    test("every type generates source that compiles in place of the artifact it replaces") {
        val failures = LinkedHashMap<String, String>()
        for (type in LocaleType.entries) {
            val workingDir = createTempDir("kotlinx-locale-compile-${type.name}")
            try {
                val sources = workingDir.resolve("src").apply { mkdirs() }
                generate(type, sources)
                val diagnostics = compile(sources, workingDir.resolve("classes"), without = listOfNotNull(type.replaces))
                if (diagnostics != null) failures[type.dslName] = diagnostics
            } finally {
                workingDir.deleteRecursively()
            }
        }
        assertEquals(
            emptyMap(),
            failures,
            "a generated type that does not compile without the artifact it replaces is a build that " +
                "excludes the shipped enum and then cannot resolve the generated one.",
        )
    }

    /** The plugin's own generation path: this feature's closure, and nothing else. */
}
