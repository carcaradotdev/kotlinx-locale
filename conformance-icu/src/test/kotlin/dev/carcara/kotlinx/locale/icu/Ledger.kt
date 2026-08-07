package dev.carcara.kotlinx.locale.icu

import java.io.File

/**
 * Why this library and ICU give different answers for one case.
 *
 * `docs/standards.md` describes four kinds of divergence. Three of them are
 * derivable from the ICU4J jar, so they are counted rather than listed: writing
 * out every locale where ICU resolved to a different bundle would be thousands
 * of rows nobody reads, and the number moving is the thing worth noticing. Only
 * [DEFECT] and [DELIBERATE] are written down one row at a time, because only a
 * person can say why they are there.
 */
enum class Divergence {

    /**
     * ICU was built from a different CLDR snapshot than the one this library
     * pins, and the value moved between them.
     *
     * Derived: ICU's own resource for the case differs from what CLDR 48.2
     * declares. Neither side is wrong; the library follows its pin.
     */
    SNAPSHOT_SKEW,

    /**
     * ICU answered from a different bundle than the locale asked for.
     *
     * Derived: ICU carries fewer locales than CLDR publishes, so for a locale
     * absent from `ULocale.getAvailableLocales()` it silently answers from an
     * ancestor. That is a non-comparison rather than a disagreement, and
     * counting it is the only honest thing to do with it.
     */
    BUNDLE_FALLBACK,

    /**
     * CLDR has data for the case and ICU shipped root's value instead.
     *
     * Derived: ICU prunes coverage below a threshold. Where CLDR says something
     * and ICU says what root says, this library follows CLDR.
     */
    ICU_PRUNED,

    /** A defect in ICU. Needs a note saying which, and ideally an upstream link. */
    DEFECT,

    /**
     * This library answers differently on purpose.
     *
     * Every one of these should correspond to an entry in `docs/boundaries.md`.
     * The note says which.
     */
    DELIBERATE,
}

/** One case where this library and ICU disagree. */
data class LedgerRow(
    val domain: String,
    val locale: String,
    val case: String,
    val ours: String,
    val icu: String,
    val divergence: Divergence,
    val note: String,
) {
    fun key(): String = "$domain\t$locale\t$case"

    fun toLine(): String = listOf(
        domain,
        locale,
        case,
        ours.escaped(),
        icu.escaped(),
        divergence.name,
        note,
    ).joinToString("\t")

    companion object {
        fun parse(line: String): LedgerRow {
            val f = line.split("\t")
            require(f.size == 7) { "a ledger row has seven tab-separated fields, got ${f.size}: $line" }
            return LedgerRow(f[0], f[1], f[2], f[3].unescaped(), f[4].unescaped(), Divergence.valueOf(f[5]), f[6])
        }
    }
}

// Tabs and newlines would break the format, and both occur in CLDR data.
private fun String.escaped(): String = replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

private fun String.unescaped(): String {
    val out = StringBuilder(length)
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c == '\\' && i + 1 < length) {
            when (this[i + 1]) {
                't' -> {
                    out.append('\t')
                    i += 2
                    continue
                }
                'n' -> {
                    out.append('\n')
                    i += 2
                    continue
                }
                '\\' -> {
                    out.append('\\')
                    i += 2
                    continue
                }
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

/**
 * The checked-in record of where this library and ICU disagree.
 *
 * Lives outside every source set, at `conformance/ledger`, so it can never reach
 * a compilation or a test binary. One file per domain, so an ICU bump produces a
 * diff a reviewer can read one domain at a time.
 *
 * Three rules, all enforced by [LedgerCheck]:
 *
 *  - a divergence that is not in the ledger fails, which is what makes this a
 *    test rather than a report;
 *  - a ledgered divergence that no longer reproduces fails, so the file cannot
 *    silently accumulate rows that stopped being true;
 *  - a [Divergence.DEFECT] or [Divergence.DELIBERATE] row with no note fails at
 *    write time, so `updateLedger` cannot produce an unexplained row.
 */
class Ledger(private val dir: File) {

    fun read(domain: String): Map<String, LedgerRow> {
        val file = dir.resolve("$domain.tsv")
        if (!file.isFile) return emptyMap()
        return file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map(LedgerRow::parse)
            .associateBy(LedgerRow::key)
    }

    fun readCounts(): Map<String, Long> {
        val file = dir.resolve("counts.tsv")
        if (!file.isFile) return emptyMap()
        return file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val (domain, kind, count) = line.split("\t")
                "$domain\t$kind" to count.toLong()
            }
    }

    fun write(domain: String, rows: List<LedgerRow>) {
        // A row that needs a human sentence gets a marker rather than being
        // refused. Refusing was the first design and it made the task useless
        // for the one job it has: on a first run every row is unexplained, so
        // there was no way to produce the file that would then be annotated.
        //
        // The rule still holds, it just moves to where it can be enforced
        // without blocking generation: `settle` fails on any committed row that
        // still carries the marker.
        val annotated = rows.map { row ->
            if (row.divergence in JUDGEMENT_CALLS && row.note.isBlank()) row.copy(note = UNEXPLAINED) else row
        }
        dir.mkdirs()
        dir.resolve("$domain.tsv").writeText(
            buildString {
                appendLine("# GENERATED by ./gradlew :conformance-icu:updateLedger. Reviewed by hand.")
                appendLine("# icu=$ICU_PIN  cldr=$CLDR_PIN")
                appendLine("#")
                appendLine("# Every row is a case where this library and ICU disagree and the")
                appendLine("# disagreement is expected. A new one fails the build; a row that stops")
                appendLine("# reproducing fails too. An ICU version bump regenerates this file, and")
                appendLine("# the diff is the point rather than a nuisance.")
                appendLine("#")
                appendLine("# domain\tlocale\tcase\tours\ticu\tdivergence\tnote")
                // Sorted, so regeneration is a diff and not a reshuffle.
                for (row in annotated.sortedBy(LedgerRow::key)) appendLine(row.toLine())
            },
        )
    }

    fun writeCounts(counts: Map<String, Long>) {
        dir.mkdirs()
        dir.resolve("counts.tsv").writeText(
            buildString {
                appendLine("# GENERATED by ./gradlew :conformance-icu:updateLedger.")
                appendLine("#")
                appendLine("# The three derivable divergence kinds, pinned by exact count rather than")
                appendLine("# listed row by row. Listing them would be thousands of lines nobody")
                appendLine("# reads; pinning the number is what catches a classifier that regressed")
                appendLine("# into excusing real bugs.")
                appendLine("#")
                appendLine("# domain\tdivergence\tcount")
                for ((key, count) in counts.toSortedMap()) appendLine("$key\t$count")
            },
        )
    }

    companion object {
        /** The two kinds only a person can tell apart, and so the only ones written out one by one. */
        val JUDGEMENT_CALLS = setOf(Divergence.DEFECT, Divergence.DELIBERATE)

        /**
         * What `updateLedger` writes into a row nobody has explained yet.
         *
         * `:conformance-icu:test` fails while any committed row still says
         * this, so a generated ledger cannot be committed as-is and the
         * explanation cannot be skipped.
         */
        const val UNEXPLAINED = "TODO: explain this divergence or reclassify it"

        fun open(): Ledger = Ledger(rootDir().resolve("conformance/ledger"))

        fun rootDir(): File = File(
            System.getProperty("kotlinx.locale.rootDir")
                ?: error("kotlinx.locale.rootDir is not set; see conformance-icu/build.gradle.kts"),
        )

        /** True when this run is meant to rewrite the ledger rather than check it. */
        val writing: Boolean get() = System.getProperty("kotlinx.locale.ledger.write") == "true"

        /** True when this run is meant to rewrite the cross-target digests. */
        val writingDigests: Boolean get() = System.getProperty("kotlinx.locale.digests.write") == "true"

        /**
         * The two generator tasks share a test source set, so each has to stand
         * aside for the other.
         *
         * Filtering by task looked like the tidier answer and does not work: the
         * Gradle plugin rewrites TESTBALLOON_INCLUDE_PATTERNS from the task's own
         * `--tests` filter, so an environment variable set in the build script is
         * overwritten before the JVM starts. Asking which run this is needs no
         * cooperation from the framework.
         */
        val isGeneratorRun: Boolean get() = writing || writingDigests
    }
}
