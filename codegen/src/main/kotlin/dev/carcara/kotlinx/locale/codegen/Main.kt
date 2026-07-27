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
    encoded["root"] = flattener.resolve("root").encode() // final runtime fallback
    for (id in flattener.localeIds) {
        encoded[id] = flattener.resolve(id).encode()
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
