package dev.carcara.kotlinx.locale.codegen

import java.io.File

fun main(args: Array<String>) {
    val mode = args.getOrNull(0) ?: "generate"
    val rootDir = File(args.getOrNull(1) ?: ".").absoluteFile

    when (mode) {
        "clone" -> {
            ensureCloned(rootDir, CLDR_REPO)
            ensureCloned(rootDir, ICU_REPO)
        }
        "generate" -> {
            val cldrDir = ensureCloned(rootDir, CLDR_REPO)
            val icuDir = ensureCloned(rootDir, ICU_REPO)
            generate(rootDir, cldrDir, icuDir)
        }
        else -> error("Unknown mode '$mode'. Use 'clone' or 'generate'.")
    }
}

private fun generate(rootDir: File, cldrDir: File, icuDir: File) {
    val supplemental = parseSupplemental(cldrDir)
    val flattener = Flattener(cldrDir, supplemental)

    println("[codegen] flattening ${flattener.localeIds.size} CLDR locales")
    val encoded = LinkedHashMap<String, String>()
    val dayPeriodGaps = LinkedHashMap<String, List<String>>()
    fun encodeChecked(id: String): String {
        val resolved = flattener.resolve(id)
        // A flexible rule type without a name renders as plain AM/PM at runtime;
        // surface how often that fallback is in play.
        val unnamed = resolved.dayPeriodRules
            .map { it.type }
            .filter { it != "am" && it != "pm" }
            .filter { resolved.dayPeriods[DAY_PERIOD_TYPES.indexOf(it) - 2].isEmpty() }
        if (unnamed.isNotEmpty()) dayPeriodGaps[id] = unnamed
        return resolved.encode()
    }
    encoded["root"] = encodeChecked("root") // final runtime fallback
    for (id in flattener.localeIds) {
        encoded[id] = encodeChecked(id)
    }
    if (dayPeriodGaps.isNotEmpty()) {
        println(
            "[codegen] ${dayPeriodGaps.size} locales have day period rules without names " +
                "(am/pm fallback), e.g. ${dayPeriodGaps.entries.take(5).joinToString { "${it.key}=${it.value}" }}",
        )
    }

    val dataDir = rootDir.resolve(
        "datetime/src/commonMain/kotlin/dev/carcara/kotlinx/locale/datetime/internal/data",
    )
    LocaleDataEmitter(dataDir, CLDR_REPO.tag).emit(encoded)

    val tagsFile = rootDir.resolve(
        "locale/src/commonMain/kotlin/dev/carcara/kotlinx/locale/internal/AvailableLocaleTags.kt",
    )
    emitAvailableLocaleTags(tagsFile, CLDR_REPO.tag, flattener.localeIds)

    val goldenFile = rootDir.resolve(
        "datetime/src/commonTest/kotlin/dev/carcara/kotlinx/locale/datetime/IcuGoldenData.kt",
    )
    emitIcuGolden(goldenFile, ICU_REPO.tag, extractIcuGolden(icuDir))

    println("[codegen] done")
}
