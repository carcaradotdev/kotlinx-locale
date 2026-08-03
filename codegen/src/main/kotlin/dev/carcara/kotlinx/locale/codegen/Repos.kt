package dev.carcara.kotlinx.locale.codegen

import java.io.File

/**
 * The pinned upstream data repositories. Both are the official, actively maintained
 * unicode-org repositories (not archived mirrors), pinned to release tags so the
 * generated output is reproducible.
 */
data class RepoSpec(val name: String, val url: String, val tag: String, val sparsePaths: List<String>)

val CLDR_REPO = RepoSpec(
    name = "cldr",
    url = "https://github.com/unicode-org/cldr.git",
    tag = "release-48-2",
    sparsePaths = listOf(
        "common/main",
        "common/supplemental",
        "common/dtd",
        "common/validity",
        // Ordinal forms ("1st", "1.") are rule-based rather than table-based, and
        // the rules live here rather than in the locale files. Only the
        // digits-ordinal rulesets are read; the spellout ones are out of scope.
        "common/rbnf",
        // Time zone identifiers: the canonical-alias map and the region of each
        // zone, which the metazone resolution needs and metaZones.xml does not
        // carry.
        "common/bcp47",
        // CLDR's own datetime cases, a second opinion on the skeleton matcher
        // that is independent of the ICU4J goldens.
        "common/testData/datetime",
        // UAX #29's segmentation rules, and the character properties they are
        // written in terms of. The grapheme cluster rules are what an initial and
        // a monogram are taken with: a written unit is not a code point, and in
        // the Indic scripts it is not one character either.
        "common/segments",
        "common/properties",
        // The person name cases, which are the conformance fixture for that
        // domain rather than a second opinion on one. They ship in the same
        // release as the tables, so unlike an ICU4J golden they carry no
        // snapshot skew.
        "common/testData/personNameTest",
    ),
)

val ICU_REPO = RepoSpec(
    name = "icu",
    url = "https://github.com/unicode-org/icu.git",
    tag = "release-78.3",
    sparsePaths = listOf(
        "icu4c/source/data/locales",
        "icu4c/source/data/misc",
        "icu4c/source/data/curr",
        "icu4c/source/data/region",
        // Not compiled against and not a dependency: DateTimePatternGenerator.java
        // is the reference for the corners UTS #35 states tersely, and reading it
        // is cheaper than guessing at them.
        "icu4j/main/core/src/main/java/com/ibm/icu/text",
        // PersonNamePattern.java is the reference for UTS #35 Part 8's
        // empty-field steps, which the specification states tersely enough that
        // reading them alone produced a rule that was wrong for four locales.
        "icu4j/main/core/src/main/java/com/ibm/icu/impl/personname",
    ),
)

/**
 * Google's libphonenumber, the source of the phone metadata.
 *
 * A third upstream alongside CLDR and ICU, and the one whose release model
 * differs most: libphonenumber ships every week or two where CLDR ships twice a
 * year, because numbering plans change on the telco's schedule rather than on a
 * standards body's. That is a reason to pin a tag and say which one, not a
 * reason to decline the data. The pin is what makes generation reproducible;
 * bumping it is a deliberate commit like bumping CLDR.
 *
 * The numbering plans themselves are ITU-T E.164, published as an Operational
 * Bulletin rather than as anything a build can read. libphonenumber is the
 * machine-readable form of it that the industry actually uses, and it is
 * Apache-2.0.
 *
 * The Java sources are checked out for the same reason ICU's are: they are the
 * reference for behaviour the XML does not describe, and reading them is cheaper
 * than guessing. Nothing here compiles against them.
 */
val PHONE_REPO = RepoSpec(
    name = "libphonenumber",
    url = "https://github.com/google/libphonenumber.git",
    tag = "v9.0.19",
    sparsePaths = listOf(
        "resources",
        "java/libphonenumber/src/com/google/i18n/phonenumbers",
        "tools/java/common/src/com/google/i18n/phonenumbers",
        // The tests, because half the edge-case fixture is the `parse(...)`
        // literals mined out of them. Without this path the mining silently
        // yields nothing and the generated cases alone still clear the size
        // check, so the fixture shrinks without anything failing.
        "java/libphonenumber/test/com/google/i18n/phonenumbers",
    ),
)

/**
 * The UTS #51 Emoji release the vendored `emoji-sequences.txt` comes from.
 *
 * Vendored rather than cloned, the way ISO 4217 list one is: it is one file, and
 * a whole repository would be fetched for it. The parser checks the file's own
 * `# Version:` header against this, so the pin and the data cannot drift apart.
 */
const val EMOJI_VERSION: String = "17.0"

/**
 * The Unicode release the vendored grapheme cluster properties come from.
 *
 * Not the newest, and deliberately so: it is the one CLDR `release-48-2` was
 * built against, which is what makes its `GraphemeBreakTest.txt` the right
 * conformance file to hold the implementation to. The two versions disagree
 * about roughly ninety cases, so taking the properties from a later release and
 * the tests from the pinned CLDR would fail for a reason that has nothing to do
 * with the code.
 *
 * Checked rather than trusted: each vendored file carries its own version in its
 * header, and parsing fails if it is not this one.
 */
const val UCD_VERSION: String = "15.1.0"

fun reposDir(rootDir: File): File = rootDir.resolve("codegen/repos")

/**
 * Shallow, blobless, sparse clone of [spec] pinned to its release tag.
 * Reuses an existing clone when the marker matches; widens the sparse
 * checkout in place when only the path set changed (blobless clones fetch
 * the missing blobs on demand).
 */
fun ensureCloned(rootDir: File, spec: RepoSpec): File {
    val dir = reposDir(rootDir).resolve(spec.name)
    val marker = dir.resolve(".pinned-tag")
    val markerContent = (listOf(spec.tag) + spec.sparsePaths).joinToString("\n")
    if (dir.resolve(".git").exists()) {
        val existing = marker.takeIf(File::exists)?.readText()
        if (existing == markerContent) {
            println("[codegen] ${spec.name} already cloned at ${spec.tag}")
            return dir
        }
        if (existing?.lineSequence()?.firstOrNull() == spec.tag) {
            println("[codegen] ${spec.name} at ${spec.tag}: updating sparse paths to ${spec.sparsePaths.joinToString()}")
            exec("git", "-C", dir.absolutePath, "sparse-checkout", "set", *spec.sparsePaths.toTypedArray())
            marker.writeText(markerContent)
            return dir
        }
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
    marker.writeText(markerContent)
    return dir
}

private fun exec(vararg command: String) {
    val process = ProcessBuilder(*command).inheritIO().start()
    val exit = process.waitFor()
    check(exit == 0) { "Command failed ($exit): ${command.joinToString(" ")}" }
}
