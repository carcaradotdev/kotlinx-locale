import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Collects what every size probe measured into one table.
 *
 * The numbers are the point of the split, so they are worth reading in one
 * place rather than scattered through the log of whichever probes happened to
 * re-run.
 */
abstract class SizeReport : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reports: ConfigurableFileCollection

    @TaskAction
    fun report() {
        val rows = reports.files
            .filter { it.isFile }
            .map { it.readText().trim().split('\t') }
            .filter { it.size == 4 }
            .sortedBy { it[1].toLong() }

        if (rows.isEmpty()) {
            logger.lifecycle("No size reports found; run checkSize first.")
            return
        }

        val table = buildString {
            appendLine()
            appendLine("| scenario | gzip | raw | budget | headroom |")
            appendLine("| --- | ---: | ---: | ---: | ---: |")
            for ((label, gzip, raw, budget) in rows.map { it }) {
                val used = gzip.toLong()
                val cap = budget.toLong()
                appendLine(
                    "| $label | ${kb(used)} | ${kb(raw.toLong())} | ${kb(cap)} | ${kb(cap - used)} |",
                )
            }
        }
        logger.lifecycle(table)
    }

    private fun kb(bytes: Long): String {
        val tenths = bytes * 10 / 1024
        return "${tenths / 10}.${tenths % 10} KB"
    }
}

private operator fun <T> List<T>.component4(): T = this[3]
