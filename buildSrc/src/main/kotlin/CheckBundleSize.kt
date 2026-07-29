import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Gzips a probe's minified Kotlin/JS bundle and fails when it exceeds its
 * budget.
 *
 * The budget exists to catch a dependency mistake as a build failure rather
 * than as 400 KB nobody notices: a `-types` module that grew an edge into a
 * `-cldr` module moves its probe by an order of magnitude. It is a ceiling,
 * not a target, so shrinking is always fine and the task says by how much when
 * the headroom gets large enough to be worth tightening.
 *
 * Sizes are gzip at the default level over the minified bundle, which is what
 * a CDN would serve and what the numbers in PLAN.md were measured at.
 */
abstract class CheckBundleSize : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bundle: ConfigurableFileCollection

    /** The ceiling in bytes, gzipped. */
    @get:Input
    abstract val budgetBytes: Property<Long>

    /** What the scenario is called in the report. */
    @get:Input
    abstract val label: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val scripts = bundle.files.filter { it.isFile && it.extension == "js" }
        check(scripts.isNotEmpty()) { "No bundle found for ${label.get()}; did the webpack task run?" }

        val raw = scripts.sumOf { it.length() }
        val gzipped = scripts.sumOf { gzippedSize(it) }
        val budget = budgetBytes.get()

        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText("${label.get()}\t$gzipped\t$raw\t$budget\n")
        }

        val headroom = budget - gzipped
        if (headroom < 0) {
            error(
                "${label.get()} is ${format(gzipped)} gzipped, over its ${format(budget)} budget by " +
                    "${format(-headroom)}. Either the dependency graph grew an edge it should not have, " +
                    "or the data did; raise the budget in the probe's build file only once you know which.",
            )
        }
        logger.lifecycle(
            "[size] ${label.get()}: ${format(gzipped)} gzipped of ${format(budget)} budget " +
                "(${format(raw)} raw)",
        )
        // A ceiling this loose has stopped measuring anything.
        if (headroom > budget / 2) {
            logger.lifecycle("[size] ${label.get()} uses under half its budget; consider tightening it")
        }
    }

    private fun gzippedSize(file: File): Long {
        val sink = ByteArrayOutputStream()
        GZIPOutputStream(sink).use { it.write(file.readBytes()) }
        return sink.size().toLong()
    }

    private fun format(bytes: Long): String =
        if (bytes < 1024) "$bytes B" else "${(bytes * 10 / 1024).let { it / 10 }}.${(bytes * 10 / 1024) % 10} KB"
}
