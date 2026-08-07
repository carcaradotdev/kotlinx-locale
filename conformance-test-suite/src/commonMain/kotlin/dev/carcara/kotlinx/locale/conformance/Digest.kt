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

package dev.carcara.kotlinx.locale.conformance

/**
 * A per-locale digest of what a source answers, for the domains that can
 * genuinely differ between targets.
 *
 * ## Why a digest rather than a golden
 *
 * The ICU goldens cover thirty locales, because a golden wide enough for eleven
 * hundred is megabytes of Kotlin source in every native test binary. The live
 * ICU comparison in `:conformance-icu` covers all of them and runs on the JVM
 * only. That leaves a gap: nothing checks that Kotlin/Native, JS and Wasm
 * compute the same answers as the JVM for the locales the goldens skip.
 *
 * A digest closes it for about nine kilobytes per domain. It is not an oracle
 * and does not claim to be: the expected value is this library's own JVM output,
 * so a match proves agreement between targets and says nothing about whether the
 * answer is right. Correctness is `:conformance-icu`'s job. This one catches a
 * `Regex`, a `Double`, a `lowercase` or a hash iteration order behaving
 * differently somewhere, which is the failure mode a JVM-only oracle cannot see.
 *
 * ## Why it is scoped to eight domains
 *
 * A name table cannot diverge across targets. `countryNameOrNull` is a lookup in
 * a `Map<String, String>` compiled from identical source by one frontend, so
 * digesting it would test the Kotlin compiler rather than this library. Only the
 * domains that touch a platform primitive are worth the bytes.
 *
 * ## Why FNV-1a and not hashCode
 *
 * `String.hashCode` is specified on the JVM and merely conventional elsewhere,
 * so pinning it would pin a stdlib implementation detail rather than this
 * library's behaviour. FNV-1a over UTF-16 code units is written out here, is the
 * same arithmetic on every target, and is stable across Kotlin releases.
 */
public object Digest {

    private const val OFFSET_BASIS: Int = -2128831035 // 0x811C9DC5
    private const val PRIME: Int = 16777619

    /** The digest of [values], joined by an explicit separator. */
    public fun of(values: List<String>): String {
        var hash = OFFSET_BASIS
        for ((index, value) in values.withIndex()) {
            if (index > 0) hash = mix(hash, SEPARATOR)
            for (char in value) hash = mix(hash, char)
        }
        return hash.toHexString()
    }

    private fun mix(hash: Int, char: Char): Int {
        val code = char.code
        var result = hash xor (code and 0xFF)
        result *= PRIME
        result = result xor ((code shr 8) and 0xFF)
        result *= PRIME
        return result
    }

    private fun Int.toHexString(): String {
        val digits = "0123456789abcdef"
        val out = CharArray(8)
        for (i in 0 until 8) {
            out[7 - i] = digits[(this shr (i * 4)) and 0xF]
        }
        return out.concatToString()
    }

    /**
     * The separator between values.
     *
     * A record separator rather than a comma, because a comma occurs inside the
     * values and would let two different lists digest identically.
     */
    private const val SEPARATOR: Char = ''
}

/**
 * Holds a source's per-locale digests to the committed ones.
 *
 * On a mismatch the failure carries the whole serialization the digest was taken
 * over, not just the two hashes. That is the difference between a digest that
 * can be debugged and one that cannot: the expected value was generated on the
 * JVM, so a JVM run agrees with it by construction and re-running there tells
 * you nothing. What you need is the failing target's own output, which is what
 * this prints.
 */
public fun assertDigestsMatch(domain: String, expected: Map<String, String>, serialize: (String) -> List<String>?) {
    val mismatches = ArrayList<String>()
    var compared = 0

    for ((tag, want) in expected) {
        val values = serialize(tag) ?: continue
        compared++
        val got = Digest.of(values)
        if (got == want) continue
        if (mismatches.size < MAX_REPORTED) {
            mismatches.add(
                buildString {
                    appendLine("$tag: expected $want but was $got")
                    appendLine("  what this target produced:")
                    for (value in values) appendLine("    ${value.ifEmpty { "<empty>" }}")
                },
            )
        }
    }

    if (compared == 0) {
        throw AssertionError(
            "$domain compared no locales, which means the digest fixture did not load. " +
                "That reports the same green as a run where everything matched.",
        )
    }
    if (mismatches.isEmpty()) return

    throw AssertionError(
        "$domain: this target disagrees with the JVM on ${mismatches.size} of $compared locales.\n" +
            "The digests are this library's own output, so a difference here is a platform " +
            "difference in the code rather than a wrong answer. Start with StdlibParityTest.\n\n" +
            mismatches.joinToString("\n"),
    )
}

private const val MAX_REPORTED = 5
