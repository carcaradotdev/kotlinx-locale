package dev.carcara.kotlinx.locale.codegen

import java.io.File

/**
 * The pinned upstream data repositories. Both are the official, actively maintained
 * unicode-org repositories (not archived mirrors), pinned to release tags so the
 * generated output is reproducible.
 */
data class RepoSpec(
    val name: String,
    val url: String,
    val tag: String,
    val sparsePaths: List<String>,
)

val CLDR_REPO = RepoSpec(
    name = "cldr",
    url = "https://github.com/unicode-org/cldr.git",
    tag = "release-48-2",
    sparsePaths = listOf("common/main", "common/supplemental", "common/dtd"),
)

val ICU_REPO = RepoSpec(
    name = "icu",
    url = "https://github.com/unicode-org/icu.git",
    tag = "release-78.3",
    sparsePaths = listOf("icu4c/source/data/locales"),
)

fun reposDir(rootDir: File): File = rootDir.resolve("codegen/repos")

/**
 * Shallow, blobless, sparse clone of [spec] pinned to its release tag.
 * Reuses an existing clone when the tag marker matches.
 */
fun ensureCloned(rootDir: File, spec: RepoSpec): File {
    val dir = reposDir(rootDir).resolve(spec.name)
    val marker = dir.resolve(".pinned-tag")
    if (dir.resolve(".git").exists() && marker.takeIf(File::exists)?.readText() == spec.tag) {
        println("[codegen] ${spec.name} already cloned at ${spec.tag}")
        return dir
    }
    dir.deleteRecursively()
    dir.parentFile.mkdirs()
    println("[codegen] cloning ${spec.url} @ ${spec.tag} (sparse: ${spec.sparsePaths.joinToString()})")
    exec(
        "git", "clone",
        "--depth", "1",
        "--branch", spec.tag,
        "--filter=blob:none",
        "--sparse",
        spec.url,
        dir.absolutePath,
    )
    exec("git", "-C", dir.absolutePath, "sparse-checkout", "set", *spec.sparsePaths.toTypedArray())
    marker.writeText(spec.tag)
    return dir
}

private fun exec(vararg command: String) {
    val process = ProcessBuilder(*command).inheritIO().start()
    val exit = process.waitFor()
    check(exit == 0) { "Command failed ($exit): ${command.joinToString(" ")}" }
}
