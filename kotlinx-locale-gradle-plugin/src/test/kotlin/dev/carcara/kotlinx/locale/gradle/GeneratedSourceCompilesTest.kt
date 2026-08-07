package dev.carcara.kotlinx.locale.gradle

import at.asitplus.testballoon.matrix.matrixSuite
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

val GeneratedSourceCompilesTest by matrixSuite {
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

    /** Null when it compiled, the compiler's own output when it did not. */
    fun compile(sources: File, destination: File): String? {
        val output = ByteArrayOutputStream()
        val exitCode = PrintStream(output, true, Charsets.UTF_8).use { stream ->
            K2JVMCompiler().exec(
                stream,
                "-nowarn",
                "-no-stdlib", // Already on the classpath below; adding it twice is a duplicate-class error.
                "-jvm-target",
                "21",
                "-classpath",
                System.getProperty("java.class.path"),
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

    /** The plugin's own generation path: this feature's closure, and nothing else. */
}
