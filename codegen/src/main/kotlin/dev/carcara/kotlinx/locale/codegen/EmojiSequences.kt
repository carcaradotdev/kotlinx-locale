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

/** One RGI flag sequence: the region it names and the two regional indicators that spell it. */
class EmojiFlagSequence(val regionCode: String, val name: String)

class EmojiData(
    /** The `# Version:` header, e.g. 17.0. */
    val version: String,
    /** The `# Date:` header, e.g. 2025-07-25. */
    val date: String,
    val flags: List<EmojiFlagSequence>,
)

private const val REGIONAL_INDICATOR_A = 0x1F1E6
private const val REGIONAL_INDICATOR_Z = 0x1F1FF

/**
 * The `RGI_Emoji_Flag_Sequence` rows of the vendored UTS #51 data.
 *
 * RGI is "recommended for general interchange": the set Unicode tells vendors to
 * support. It is not a rendering guarantee, and it is not the ISO 3166-1 set. At
 * Emoji 17.0 it carries 259 sequences, which is every code this library models
 * plus ten it does not: the exceptionally reserved codes (AC, CP, DG, EA, IC,
 * TA), the user-assigned XK for Kosovo, CQ for Sark, and the EU and UN flags,
 * which are not countries at all.
 *
 * The file is vendored under codegen resources the way ISO 4217 list one is: it
 * is one file on its own release cycle, and cloning a repository for it would
 * cost more than keeping a copy. Nothing here is compiled into an artifact.
 * `Country.flagEmoji` derives its sequence arithmetically, and this data exists
 * to prove at generation time that the derivation lands on a sequence Unicode
 * actually recommends.
 */
fun parseEmojiSequences(): EmojiData {
    val stream = EmojiData::class.java.getResourceAsStream("/emoji/emoji-sequences.txt")
        ?: error("emoji/emoji-sequences.txt is missing from codegen resources")
    var version = ""
    var date = ""
    val flags = ArrayList<EmojiFlagSequence>()

    stream.bufferedReader().forEachLine { line ->
        if (line.startsWith("#")) {
            val comment = line.removePrefix("#").trim()
            when {
                comment.startsWith("Version:") -> version = comment.removePrefix("Version:").trim()
                comment.startsWith("Date:") -> date = comment.removePrefix("Date:").trim().substringBefore(',')
            }
            return@forEachLine
        }
        val fields = line.substringBefore('#').split(';')
        if (fields.size < 3) return@forEachLine
        if (fields[1].trim() != "RGI_Emoji_Flag_Sequence") return@forEachLine

        val codePoints = fields[0].trim().split(' ').map { it.toInt(16) }
        check(codePoints.size == 2) { "a flag sequence is two code points, not ${codePoints.size}: $line" }
        for (codePoint in codePoints) {
            check(codePoint in REGIONAL_INDICATOR_A..REGIONAL_INDICATOR_Z) {
                "a flag sequence is two regional indicators, and U+${codePoint.toString(16).uppercase()} is not one: $line"
            }
        }
        val region = codePoints.map { 'A' + (it - REGIONAL_INDICATOR_A) }.joinToString("")
        flags += EmojiFlagSequence(region, fields[2].trim().removePrefix("flag:").trim())
    }

    check(version.isNotEmpty()) { "emoji-sequences.txt carries no Version header" }
    check(flags.size > 240) { "emoji-sequences.txt: implausibly few flag sequences (${flags.size})" }
    return EmojiData(version, date, flags.sortedBy(EmojiFlagSequence::regionCode))
}

/**
 * Region codes this build knows Unicode has no flag for.
 *
 * Empty, and it should stay that way. It exists so that a CLDR release adding a
 * region ahead of the Emoji release that gives it a flag produces a decision
 * with a note attached, rather than a scramble under time pressure or a silent
 * `Country.flagEmoji` that renders as two letters in a box.
 */
val KNOWN_FLAGLESS_REGIONS: Set<String> = emptySet()

/**
 * Fails generation if any country would derive a flag sequence Unicode does not
 * recommend.
 *
 * `Country.flagEmoji` is arithmetic on the alpha-2 code and carries no table, so
 * it cannot be wrong about a country it has. What it can be wrong about is
 * whether the result is a sequence anything renders, and that is a fact about a
 * Unicode release rather than about this code. Checking it here is what lets the
 * property have no fallback and no nullable form.
 */
fun crossCheckCountryFlags(countries: List<CountryInfo>): EmojiData {
    val emoji = parseEmojiSequences()
    check(emoji.version == EMOJI_VERSION) {
        "the vendored emoji-sequences.txt says Emoji ${emoji.version} but EMOJI_VERSION says $EMOJI_VERSION"
    }
    val rgi = emoji.flags.mapTo(HashSet(), EmojiFlagSequence::regionCode)
    val flagless = countries.map(CountryInfo::alpha2).filterNot { it in rgi || it in KNOWN_FLAGLESS_REGIONS }
    check(flagless.isEmpty()) {
        "Country.flagEmoji derives a regional indicator sequence for every entry, and this build would " +
            "derive one Unicode does not list as RGI: ${flagless.joinToString()}. Either update the " +
            "vendored emoji-sequences.txt and EMOJI_VERSION, or add these to KNOWN_FLAGLESS_REGIONS with " +
            "a note saying which Emoji release is expected to carry them."
    }
    println(
        "[codegen] Emoji ${emoji.version} (${emoji.date}): all ${countries.size} countries have an RGI flag " +
            "(${rgi.size - countries.size} RGI codes are not ISO 3166-1 countries)",
    )
    return emoji
}
