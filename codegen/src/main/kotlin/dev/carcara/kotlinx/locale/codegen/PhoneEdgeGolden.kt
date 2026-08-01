package dev.carcara.kotlinx.locale.codegen

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.io.File

/**
 * The parser held to libphonenumber over the inputs that are not well behaved.
 *
 * The conformance fixture next to this one asks about each territory's own
 * example number, which is by construction the easiest input that territory has:
 * correct length, correct prefix, no punctuation, no extension. Agreeing on
 * those says the tables were read correctly and very little about the parser,
 * because almost none of its decisions are reached.
 *
 * This one is about the decisions. Two sources, deliberately different in kind:
 *
 * The mined cases are every `parse` literal in libphonenumber's own test suite,
 * which is a list of the inputs its authors thought were worth pinning after
 * fifteen years of bug reports. Nobody would derive `+1 (650) 253-0000x1234` or
 * a number written in letters from first principles.
 *
 * The generated cases are each territory's example number put through the
 * mutations a real input goes through: written as E.164, as its national form,
 * with and without the national prefix, reached through the international
 * dialling prefix instead of a plus, wrapped in the punctuation people type,
 * with an extension in each of the spellings that means one, and finally one
 * digit short and one digit long so that the length boundary is tested from both
 * sides in every territory rather than in the handful someone thought to list.
 *
 * The recorded answer includes rejection. Half the value here is agreeing about
 * what is *not* a number, and a fixture that only carried the successes would
 * pass for a parser that accepted everything.
 */

/** One input and what libphonenumber makes of it. */
class PhoneEdgeCase(
    val input: String,
    /** The default region, or `""` for none. */
    val region: String,
    /** `null` when libphonenumber rejects the input. */
    val parsed: PhoneEdgeAnswer?,
)

class PhoneEdgeAnswer(val e164: String, val isValid: Boolean, val type: String, val region: String)

fun extractPhoneEdgeGolden(repoDir: File, metadata: PhoneMetadata): List<PhoneEdgeCase> {
    val util = PhoneNumberUtil.getInstance()
    val inputs = LinkedHashSet<Pair<String, String>>()

    inputs += minedCases(repoDir)
    val minedCount = inputs.size
    inputs += generatedCases(util, metadata)

    val cases = inputs.map { (input, region) ->
        val answer = try {
            val number = if (region.isEmpty()) util.parse(input, null) else util.parse(input, region)
            PhoneEdgeAnswer(
                e164 = util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164),
                isValid = util.isValidNumber(number),
                type = util.getNumberType(number).name,
                // `001` is libphonenumber's placeholder for a number that
                // belongs to no country, which `+800` international freephone
                // is. This library types that as no country rather than as a
                // country called 001, so the two spellings of the same fact are
                // reconciled here rather than asserted apart.
                region = util.getRegionCodeForNumber(number).orEmpty().takeIf { it != "001" }.orEmpty(),
            )
        } catch (_: NumberParseException) {
            null
        } catch (_: RuntimeException) {
            // A few mined literals are arguments to calls that are expected to
            // throw something other than NumberParseException; they are not
            // parser cases and are dropped rather than recorded as rejections.
            return@map null
        }
        PhoneEdgeCase(input, region, answer)
    }.filterNotNull()

    val rejected = cases.count { it.parsed == null }
    println(
        "[codegen] phone edge cases: ${cases.size} inputs " +
            "($minedCount mined from libphonenumber's tests, ${cases.size - minedCount} generated); " +
            "$rejected are rejections",
    )
    check(cases.size > 2000) { "expected a broad edge-case set, got ${cases.size}" }
    check(rejected > 200) { "a fixture that agrees only about what parses proves half of what it should" }
    return cases
}

/** Every `parse("…", RegionCode.XX)` literal in libphonenumber's own tests. */
private fun minedCases(repoDir: File): List<Pair<String, String>> {
    val testDir = repoDir.resolve("java/libphonenumber/test/com/google/i18n/phonenumbers")
    if (!testDir.isDirectory) {
        println("[codegen] WARNING: libphonenumber tests not checked out; mining no edge cases")
        return emptyList()
    }
    val pattern = Regex("""parse\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*(?:RegionCode\.([A-Z0-9_]+)|(null))\s*\)""")
    val found = LinkedHashSet<Pair<String, String>>()
    for (file in testDir.walkTopDown().filter { it.extension == "java" }) {
        for (match in pattern.findAll(file.readText())) {
            val literal = unescapeJava(match.groupValues[1])
            val region = match.groupValues[2].takeIf { it.isNotEmpty() && it != "ZZ" && it.length == 2 }.orEmpty()
            found += literal to region
        }
    }
    return found.toList()
}

/** Java string escapes, which the mined literals carry for the unicode digits. */
private fun unescapeJava(text: String): String = buildString(text.length) {
    var index = 0
    while (index < text.length) {
        val ch = text[index]
        if (ch != '\\' || index + 1 >= text.length) {
            append(ch)
            index++
            continue
        }
        when (val escape = text[index + 1]) {
            'u' -> {
                append(text.substring(index + 2, index + 6).toInt(16).toChar())
                index += 6
            }
            'n' -> {
                append('\n')
                index += 2
            }
            't' -> {
                append('\t')
                index += 2
            }
            else -> {
                append(escape)
                index += 2
            }
        }
    }
}

/**
 * Each territory's example number, written the ways a real input is written.
 *
 * Built from the metadata rather than listed, so a territory added by a later
 * libphonenumber release is covered without anyone remembering to add it.
 */
private fun generatedCases(util: PhoneNumberUtil, metadata: PhoneMetadata): List<Pair<String, String>> {
    val cases = LinkedHashSet<Pair<String, String>>()
    for (territory in metadata.territories) {
        if (territory.id == "001") continue
        val example = territory.descriptions[PhoneDescType.FIXED_LINE]?.exampleNumber
            ?: territory.descriptions[PhoneDescType.MOBILE]?.exampleNumber
            ?: continue
        val code = territory.countryCode
        val region = territory.id
        val national = territory.nationalPrefix.orEmpty() + example
        val idd = territory.internationalPrefix
            // The prefix is a pattern; the shortest all-digit alternative is the
            // one someone would actually dial.
            ?.split('|')?.map { it.filter(Char::isDigit) }?.filter { it.isNotEmpty() }?.minByOrNull { it.length }

        // Written forms that should all reach the same number.
        cases += "+$code$example" to ""
        cases += "+$code$example" to region
        cases += "+$code $example" to region
        cases += "$example" to region
        cases += national to region
        cases += "  +$code$example  " to region
        cases += "+$code-$example" to region
        cases += "+$code ($example)" to region
        cases += "+$code.$example" to region
        cases += "+$code/$example" to region
        if (idd != null) cases += "$idd$code$example" to region

        // Extensions, in each spelling that means one.
        cases += "+$code${example}ext.123" to region
        cases += "+$code$example x123" to region
        cases += "+$code$example;ext=123" to region
        cases += "+$code$example#123" to region

        // The length boundary, from both sides.
        if (example.length > 2) cases += "+$code${example.dropLast(1)}" to region
        cases += "+$code${example}9" to region
        cases += "+$code${example}99" to region

        // Inputs that are not numbers, or are numbers of nowhere.
        cases += "+$example" to region
        cases += "abc$example" to region
        cases += "+999$example" to region
        cases += "" to region
        cases += "+" to region
        cases += "0" to region
    }
    // Only the ones libphonenumber's own metadata knows the region for, so a
    // region it does not support cannot make the whole fixture unreadable.
    val supported = util.supportedRegions
    return cases.filter { (_, region) -> region.isEmpty() || region in supported }
}

fun emitPhoneEdgeGolden(outputFile: File, tag: String, cases: List<PhoneEdgeCase>) {
    outputFile.parentFile.mkdirs()
    outputFile.writeText(
        buildString {
            append("// GENERATED by :codegen from libphonenumber $tag. Do not edit.\n")
            append("// Regenerate with: ./gradlew :codegen:generateLocaleData\n")
            append("package dev.carcara.kotlinx.locale.phone.conformance\n\n")
            append("/** One input, the default region, and what libphonenumber makes of it. */\n")
            append("public class PhoneEdgeCase(\n")
            append("    public val input: String,\n")
            append("    /** Empty for no default region. */\n")
            append("    public val region: String,\n")
            append("    /** Null when libphonenumber rejects the input. */\n")
            append("    public val e164: String?,\n")
            append("    public val isValid: Boolean,\n")
            append("    public val type: String,\n")
            append("    public val numberRegion: String,\n")
            append(")\n\n")
            append("/** Inputs that exercise the parser rather than the tables. */\n")
            append("public val phoneEdgeCases: List<PhoneEdgeCase> = decodePhoneEdgeCases(\n")
            // One delimited record per case: 3500 constructor calls is a source
            // file no compiler should be asked to read.
            for (chunk in cases.chunked(400)) {
                append("    \"")
                append(
                    kotlinEscape(
                        chunk.joinToString(LIST_SEPARATOR) { case ->
                            listOf(
                                case.input,
                                case.region,
                                case.parsed?.e164.orEmpty(),
                                if (case.parsed == null) {
                                    ""
                                } else if (case.parsed.isValid) {
                                    "1"
                                } else {
                                    "0"
                                },
                                case.parsed?.type.orEmpty(),
                                case.parsed?.region.orEmpty(),
                            ).joinToString(FIELD_SEPARATOR)
                        },
                    ),
                )
                append("\",\n")
            }
            append(")\n\n")
            append("private fun decodePhoneEdgeCases(vararg chunks: String): List<PhoneEdgeCase> =\n")
            append("    chunks.flatMap { chunk ->\n")
            append("        chunk.split('\\u001E').map { row ->\n")
            append("            val f = row.split('\\u001F')\n")
            append("            PhoneEdgeCase(f[0], f[1], f[2].takeIf { f[3].isNotEmpty() }, f[3] == \"1\", f[4], f[5])\n")
            append("        }\n")
            append("    }\n")
        },
    )
    println("[codegen] emitted ${cases.size} phone edge cases to $outputFile")
}
