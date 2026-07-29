import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Collects what every size probe measured into one table.
 *
 * The numbers are the point of the split, so they are worth reading in one place
 * rather than scattered through the log of whichever probes happened to re-run.
 */
@DisableCachingByDefault(because = "it prints a summary of other tasks' outputs and produces nothing of its own")
abstract class SizeReport : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reports: ConfigurableFileCollection

    /**
     * How many probes the build declares.
     *
     * Checked rather than trusted: a probe added to `gradle/size-probes.txt` but
     * missing from the report would otherwise just be a row nobody notices is
     * absent.
     */
    @get:Input
    abstract val expectedProbes: Property<Int>

    @TaskAction
    fun report() {
        val rows = reports.files
            .filter { it.isFile }
            .map { it.readText().trim().split('\t') }
            .filter { it.size == 4 }
            .sortedBy { it[1].toLong() }

        val table = buildString {
            appendLine()
            appendLine("| scenario | gzip | raw | budget | headroom |")
            appendLine("| --- | ---: | ---: | ---: | ---: |")
            for (row in rows) {
                val used = row[1].toLong()
                val cap = row[3].toLong()
                appendLine("| ${row[0]} | ${kb(used)} | ${kb(row[2].toLong())} | ${kb(cap)} | ${kb(cap - used)} |")
            }
        }
        logger.lifecycle(table)

        val expected = expectedProbes.get()
        check(rows.size == expected) {
            "expected $expected size probes but got ${rows.size}; a probe in gradle/size-probes.txt " +
                "is not offering a report, or its checkSize task did not run"
        }
    }

    private fun kb(bytes: Long): String {
        val tenths = bytes * 10 / 1024
        return "${tenths / 10}.${tenths % 10} KB"
    }
}
