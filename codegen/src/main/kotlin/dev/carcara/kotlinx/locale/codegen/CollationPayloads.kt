/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.carcara.kotlinx.locale.codegen

import java.io.File

/**
 * CLDR's simple language aliases, as `alias` to `canonical`.
 *
 * Only the single-subtag ones, which is all the collation directory needs: the
 * files are named for a language and the aliases that matter here rename one.
 */
internal fun languageAliases(cldrDir: File): Map<String, String> {
    val aliases = LinkedHashMap<String, String>()

    val metadata = cldrDir.resolve("common/supplemental/supplementalMetadata.xml")
    if (metadata.isFile) {
        val pattern = Regex("""<languageAlias\s+type="([a-z]{2,3})"\s+replacement="([a-z]{2,3})"""")
        for (match in pattern.findAll(metadata.readText())) {
            aliases[match.groupValues[1]] = match.groupValues[2]
        }
    }

    // `parentLocales` too, which is the other way CLDR says one locale reads
    // another's file. Nynorsk is the case that needs it: `nn` is not an alias of
    // `no`, it is a language whose declared parent is `no`, and `nn.xml` carries
    // no rules of its own.
    val supplemental = cldrDir.resolve("common/supplemental/supplementalData.xml")
    if (supplemental.isFile) {
        val pattern = Regex("""<parentLocale\s+parent="([a-zA-Z_]+)"\s+locales="([^"]+)"""")
        for (match in pattern.findAll(supplemental.readText())) {
            val parent = canonicalTag(match.groupValues[1])
            for (child in match.groupValues[2].split(' ').filter(String::isNotEmpty)) {
                aliases.putIfAbsent(canonicalTag(child), parent)
            }
        }
    }

    return aliases
}

/** The three collation payloads: the shared pair, and one delta per locale. */
class CollationPayloads(val root: String, val normalization: String, val tailorings: Map<String, String>)

/**
 * Reads the root table once and folds every CLDR tailoring over it.
 *
 * The tailorings are keyed by canonical tag rather than by CLDR file id, and
 * only the locales CLDR actually tailors get an entry. The other thousand reach
 * one of them, or root, through the ordinary parent walk, which is what
 * `resolvedRecord` already does for every other table.
 *
 * A tailoring that cannot be built stops the generation. A locale that silently
 * fell back to root order would sort visibly wrongly with nothing saying so.
 */
fun buildCollationPayloads(cldrDir: File): CollationPayloads {
    val root = parseFractionalUca(cldrDir.resolve("common/uca/FractionalUCA.txt"))
    val ranks = WeightRanks.of(root)
    val normalization = parseNormalizationData()

    val collationDir = cldrDir.resolve("common/collation")
    val deltas = LinkedHashMap<String, String>()
    val files = collationDir.listFiles()?.filter { it.name.endsWith(".xml") }?.sortedBy(File::getName).orEmpty()
    val resolveImport: (String) -> String? = { id -> importedRules(collationDir, id) }
    for (file in files) {
        val rules = collationRules(file)
        if (rules.isEmpty()) continue
        val tag = canonicalTag(file.nameWithoutExtension)
        deltas[tag] = try {
            tailoringFor(root, ranks, normalization, rules, resolveImport).encodeDelta()
        } catch (e: Exception) {
            throw IllegalStateException("collation tailoring for ${file.name} could not be built", e)
        }
    }
    println("[codegen] built ${deltas.size} collation tailorings from ${files.size} CLDR files")

    // One entry per tailoring, and root, and nothing else.
    //
    // Writing an empty entry for every locale in the build looks tidier and is
    // wrong: `resolvedRecord` takes the first of a locale's lookup tags that the
    // registry carries, so an empty `as-IN` shadows the real `as` and Assamese
    // falls back to root order. CLDR tailors 112 locales and the other thousand
    // reach one of them, or root, by the ordinary parent walk.
    val payloads = LinkedHashMap<String, String>(deltas.size + 1)
    payloads["root"] = ""
    payloads.putAll(deltas)

    // CLDR's language aliases, which the collation directory is named by and a
    // BCP 47 tag is not. `nb.xml` is empty because CLDR files Norwegian Bokmål
    // under `no`, and the parent walk cannot find it: `no` is not an ancestor of
    // `nb`, it is another name for it. Without this Norwegian sorts å second
    // rather than last.
    for ((from, to) in languageAliases(cldrDir)) {
        val delta = deltas[to] ?: continue
        if (from !in payloads) payloads[from] = delta
    }

    return CollationPayloads(
        root = encodeCollationRoot(root, ranks),
        normalization = encodeNormalizationData(normalization),
        tailorings = payloads,
    )
}
