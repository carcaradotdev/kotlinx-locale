import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// Each scenario resolves a different npm dependency set (the datetime module
// pulls in @js-joda through kotlinx-datetime, the baseline pulls in nothing),
// so a pinned lock file would fail the build every time the scenario changes.
// The lock here is a build artifact, not a checked-in one. See .gitignore.
plugins.withType<YarnPlugin> {
    the<YarnRootExtension>().apply {
        yarnLockAutoReplace = true
        yarnLockMismatchReport = YarnLockMismatchReport.NONE
        reportNewYarnLock = false
    }
}

val rootProperties: Properties = Properties().apply {
    file("../../gradle.properties").inputStream().use { load(it) }
}

val versionCatalog: String = file("../../gradle/libs.versions.toml").readText()

fun catalogVersion(name: String): String =
    Regex("""^$name\s*=\s*"([^"]+)"""", RegexOption.MULTILINE).find(versionCatalog)!!.groupValues[1]

data class Facade(val dependency: String, val requires: List<String>)

fun libraryModule(name: String, vararg requires: String) = Facade(
    dependency = "${rootProperties.getProperty("group")}:$name:${rootProperties.getProperty("version")}",
    requires = requires.toList(),
)

/**
 * Builds a Kotlin/JS bundle that re-exports the whole public API of a chosen set
 * of library modules through `@JsExport`, so that Kotlin's DCE and webpack's
 * tree shaking cannot drop anything a JS/TypeScript consumer could reach.
 *
 * Pick the modules with `-Pmodules=`, a comma-separated list of the names below,
 * or `all`, or `none` (the empty baseline that measures the Kotlin/JS runtime
 * floor alone). Selecting a module also selects the modules it exposes through
 * `api` dependencies, because their types are part of its public surface.
 *
 * `kotlinx-datetime` is not one of ours: it is the transitive dependency the
 * datetime module exposes, measured on its own so that the datetime figure can
 * be read as "our code plus this".
 */
val moduleFacades = mapOf(
    "locale" to libraryModule("kotlinx-locale"),
    "country" to libraryModule("kotlinx-locale-country", "locale"),
    "currency" to libraryModule("kotlinx-locale-currency", "locale", "country"),
    "datetime" to libraryModule("kotlinx-locale-datetime", "locale", "kotlinx-datetime"),
    "kotlinx-datetime" to Facade(
        dependency = "org.jetbrains.kotlinx:kotlinx-datetime:${catalogVersion("kotlinx-datetime")}",
        requires = emptyList(),
    ),
)

val selectedModules: List<String> = run {
    val raw = (findProperty("modules") as String? ?: "all").trim()
    val requested = when (raw) {
        // `all` means all of ours; kotlinx-datetime comes along through datetime.
        "all" -> moduleFacades.keys.filter { it != "kotlinx-datetime" }
        "none", "" -> emptyList()
        else -> raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
    requested.forEach {
        require(it in moduleFacades) {
            "Unknown module '$it'. Known modules: ${moduleFacades.keys.joinToString(", ")}, all, none."
        }
    }
    // Close over `api` dependencies and keep a stable order for reproducible output.
    val closure = mutableSetOf<String>()
    fun add(name: String) {
        if (!closure.add(name)) return
        moduleFacades.getValue(name).requires.forEach(::add)
    }
    requested.forEach(::add)
    moduleFacades.keys.filter { it in closure }
}

kotlin {
    jvmToolchain(21)

    js {
        binaries.executable()
        generateTypeScriptDefinitions()
        browser {
            commonWebpackConfig {
                outputFileName = "probe.js"
            }
            // The probe is never executed; it only has to link and bundle.
            testTask { enabled = false }
        }
    }

    sourceSets {
        jsMain {
            kotlin.setSrcDirs(
                listOf("src/probe/base/kotlin") + selectedModules.map { "src/probe/$it/kotlin" },
            )
            dependencies {
                selectedModules.forEach { module ->
                    implementation(moduleFacades.getValue(module).dependency)
                }
            }
        }
    }
}

/** Prints the resolved scenario so the measuring script can label its results. */
tasks.register("printScenario") {
    val resolved = selectedModules
    doLast {
        println(if (resolved.isEmpty()) "none" else resolved.joinToString(","))
    }
}
