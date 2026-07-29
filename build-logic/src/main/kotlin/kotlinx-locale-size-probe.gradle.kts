/**
 * A Kotlin/JS probe that measures what one dependency set costs a consumer.
 *
 * Kotlin/JS is the only target with dead code elimination, so it is the only one
 * where "what does this artifact actually cost" has an answer a build can check.
 * The same module boundaries are what buy the saving on JVM, Android and Native,
 * where nothing is eliminated and the dependency block is the only lever there
 * is.
 *
 * Each probe declares its budget:
 *
 * ```
 * sizeProbe {
 *     budgetBytes = 30 * 1024
 * }
 * ```
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("kotlinx-locale-ktlint")
}

interface SizeProbeExtension {
    /** The gzipped ceiling for this probe's minified bundle. */
    val budgetBytes: Property<Long>
}

val sizeProbe = extensions.create<SizeProbeExtension>("sizeProbe")

kotlin {
    jvmToolchain(21)

    js {
        // Webpack in production mode is what produces a single minified bundle;
        // no browser is launched, since these probes have no tests.
        browser()
        binaries.executable()
    }
}

val webpack = tasks.named("jsBrowserProductionWebpack")

val checkSize = tasks.register<CheckBundleSize>("checkSize") {
    group = "verification"
    description = "Gzips the production bundle and fails when it exceeds this probe's budget"
    // Taking the task's outputs as a tree carries the dependency and skips the
    // source maps, which are not shipped.
    bundle.from(webpack.map { it.outputs.files.asFileTree })
    budgetBytes = sizeProbe.budgetBytes
    label = project.name.removePrefix("probe-")
    report = layout.buildDirectory.file("reports/size/bundle.tsv")
}

// Offer the measurement to whoever aggregates it. The root project consumes this
// configuration, so it never has to name this project's tasks.
val sizeReportElements = configurations.consumable("sizeReportElements") {
    attributes { attribute(LocaleAttributes.KIND, LocaleAttributes.SIZE_REPORT) }
}

artifacts.add(sizeReportElements.name, checkSize.flatMap { it.report })
