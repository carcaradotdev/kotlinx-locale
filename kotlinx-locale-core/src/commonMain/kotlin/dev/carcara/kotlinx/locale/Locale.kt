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

package dev.carcara.kotlinx.locale

/**
 * An immutable Unicode locale identifier: a Unicode language identifier plus
 * optional locale extensions.
 *
 * Subtags are normalized: lowercase language, variant and extensions, title-case
 * script, uppercase region. Extensions are held in the canonical order UTS #35
 * Annex C defines, and [toLanguageTag] writes them back in it.
 *
 * Of the `-u-` keys, only `hc` changes what this library renders. `nu`, `ca` and
 * the rest are kept and round-tripped, and do nothing. That statement is what
 * UAX35-C2 asks an implementation to make.
 *
 * Data lookup runs on the language identifier alone, so two identifiers that
 * differ only in an extension read the same tables.
 */
public class Locale private constructor(
    public val language: String,
    public val script: String?,
    public val region: String?,
    public val variant: String?,
    private val attributes: List<String>,
    private val keywords: Map<String, String>,
    private val otherExtensions: Map<Char, String>,
) {

    /**
     * The `-u-` attributes this identifier carries. Never acted on.
     */
    public val unicodeAttributes: Set<String> get() = attributes.sorted().toSet()

    /**
     * The value of a `-u-` keyword, or `null` when the identifier names none.
     * An absent value reads as `true`, which is what UTS #35 assumes.
     *
     * Only `hc` changes what this library renders. Every other key round-trips
     * through [toLanguageTag] and has no effect.
     */
    public fun unicodeKeywordOrNull(key: String): String? = keywords[key.lowercase()]

    /**
     * This identifier with [key] set to [value], or removed when [value] is `null`.
     *
     * @throws IllegalArgumentException if [key] or [value] is not well-formed `-u-`
     * syntax. This is a syntax check only, never whether [key] is a keyword this
     * library recognises.
     */
    public fun withUnicodeKeyword(key: String, value: String?): Locale {
        val normalized = key.lowercase()
        require(normalized.length == 2 && normalized[0].isAsciiAlphanumeric() && normalized[1].isLatinLetter()) {
            "Invalid Unicode keyword key: '$key'"
        }
        val normalizedValue = value?.lowercase()?.also {
            require(isWellFormedUnicodeValue(it)) { "Invalid Unicode keyword value: '$value'" }
        }
        val updated = if (normalizedValue == null) keywords - normalized else keywords + (normalized to normalizedValue)
        if (updated == keywords) return this
        return Locale(language, script, region, variant, attributes, updated, otherExtensions)
    }

    /** The Unicode language identifier alone, with every extension removed. */
    public fun stripExtensions(): Locale = if (attributes.isEmpty() && keywords.isEmpty() && otherExtensions.isEmpty()) {
        this
    } else {
        Locale(language, script, region, variant, emptyList(), emptyMap(), emptyMap())
    }

    /** The canonical BCP 47 language tag, e.g. `pt-BR` or `sr-Cyrl-BA`. */
    public fun toLanguageTag(): String = buildString {
        append(language)
        script?.let { append('-').append(it) }
        region?.let { append('-').append(it) }
        variant?.let { append('-').append(it) }
        appendExtensions()
    }

    private fun StringBuilder.appendExtensions() {
        val singletons = buildList {
            if (attributes.isNotEmpty() || keywords.isNotEmpty()) add('u')
            addAll(otherExtensions.keys)
        }.sortedWith(compareBy({ it == 'x' }, { it }))
        for (singleton in singletons) {
            append('-').append(singleton)
            if (singleton == 'u') appendUnicodeExtension() else append('-').append(otherExtensions.getValue(singleton))
        }
    }

    private fun StringBuilder.appendUnicodeExtension() {
        for (attribute in attributes.sorted()) append('-').append(attribute)
        for (key in keywords.keys.sorted()) {
            append('-').append(key)
            val value = keywords.getValue(key)
            if (value != TRUE_VALUE) append('-').append(value)
        }
    }

    override fun toString(): String = toLanguageTag()

    override fun equals(other: Any?): Boolean = other is Locale &&
        language == other.language &&
        script == other.script &&
        region == other.region &&
        variant == other.variant &&
        attributes.sorted() == other.attributes.sorted() &&
        keywords == other.keywords &&
        otherExtensions == other.otherExtensions

    override fun hashCode(): Int {
        var result = language.hashCode()
        result = 31 * result + (script?.hashCode() ?: 0)
        result = 31 * result + (region?.hashCode() ?: 0)
        result = 31 * result + (variant?.hashCode() ?: 0)
        result = 31 * result + attributes.sorted().hashCode()
        result = 31 * result + keywords.hashCode()
        result = 31 * result + otherExtensions.hashCode()
        return result
    }

    public companion object {

        /**
         * Creates a locale from individual subtags, normalizing their case.
         *
         * @throws IllegalArgumentException if [language] is not a 2-8 letter code.
         */
        public fun of(language: String, script: String? = null, region: String? = null, variant: String? = null): Locale {
            require(language.length in 2..8 && language.all(Char::isLatinLetter)) {
                "Invalid language subtag: '$language'"
            }
            return Locale(
                language = legacyLanguageAlias(language.lowercase()),
                script = script?.let {
                    require(it.length == 4 && it.all(Char::isLatinLetter)) { "Invalid script subtag: '$it'" }
                    it.lowercase().replaceFirstChar(Char::uppercaseChar)
                },
                region = region?.let {
                    require(
                        (it.length == 2 && it.all(Char::isLatinLetter)) ||
                            (it.length == 3 && it.all(Char::isAsciiDigit)),
                    ) { "Invalid region subtag: '$it'" }
                    it.uppercase()
                },
                variant = variant?.takeIf(String::isNotEmpty)?.lowercase(),
                attributes = emptyList(),
                keywords = emptyMap(),
                otherExtensions = emptyMap(),
            )
        }

        /**
         * Parses a language tag leniently: accepts BCP 47 (`pt-BR`), the `-u-`,
         * `-t-` and `-x-` extensions, and POSIX-style identifiers
         * (`pt_BR.UTF-8@latin`) including the old `@hc=h23` keyword syntax.
         *
         * Returns `null` when no valid language subtag can be extracted.
         */
        public fun forLanguageTagOrNull(tag: String): Locale? {
            val atIndex = tag.indexOf('@')
            val head = if (atIndex >= 0) tag.substring(0, atIndex) else tag
            val oldKeywords = if (atIndex >= 0) tag.substring(atIndex + 1) else ""
            val cleaned = head.substringBefore('.').trim()
            if (cleaned.isEmpty()) return null
            val parts = cleaned.split('-', '_').filter(String::isNotEmpty)
            if (parts.isEmpty()) return null

            val language = parts[0].lowercase()
            if (language.length !in 2..8 || !language.all(Char::isLatinLetter)) return null
            if (language == "c" || language == "posix") return null

            var script: String? = null
            var region: String? = null
            var variant: String? = null
            var index = 1
            while (index < parts.size && parts[index].length != 1) {
                val part = parts[index]
                when {
                    script == null &&
                        region == null &&
                        variant == null &&
                        part.length == 4 &&
                        part.all(Char::isLatinLetter) ->
                        script = part.lowercase().replaceFirstChar(Char::uppercaseChar)

                    region == null &&
                        variant == null &&
                        (
                            (part.length == 2 && part.all(Char::isLatinLetter)) ||
                                (part.length == 3 && part.all(Char::isAsciiDigit))
                            ) ->
                        region = part.uppercase()

                    variant == null -> variant = part.lowercase()
                }
                index++
            }

            val extensions = parseExtensions(parts, index).mergedWith(parseOldKeywords(oldKeywords))
            return Locale(
                language = legacyLanguageAlias(language),
                script = script,
                region = region,
                variant = variant,
                attributes = extensions.attributes,
                keywords = extensions.keywords,
                otherExtensions = extensions.other,
            )
        }

        /**
         * Like [forLanguageTagOrNull] but throws on tags without a valid language subtag.
         */
        public fun forLanguageTag(tag: String): Locale = requireNotNull(forLanguageTagOrNull(tag)) { "Cannot parse language tag: '$tag'" }

        /**
         * The locale [LocaleDefaults] names, the platform's own when it names
         * none, or `en` when the platform exposes none either (e.g. WASI).
         *
         * The platform's own carries the device hour cycle as an `hc` keyword
         * where the platform reports one. A locale set on [LocaleDefaults]
         * replaces it, hour cycle included.
         */
        public val current: Locale
            get() = LocaleDefaults.locale ?: platformCurrent()

        private fun platformCurrent(): Locale {
            val base = platformSystemLocaleTag()?.let(::forLanguageTagOrNull) ?: of("en")
            val keyword = platformHourCycleKeyword()?.takeIf(::isWellFormedUnicodeValue) ?: return base
            return base.withUnicodeKeyword("hc", keyword)
        }
    }
}

private fun legacyLanguageAlias(language: String): String = when (language) {
    "iw" -> "he"
    "in" -> "id"
    "ji" -> "yi"
    "mo" -> "ro"
    "tl" -> "fil"
    else -> language
}

private fun Char.isLatinLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
private fun Char.isAsciiAlphanumeric(): Boolean = isLatinLetter() || isAsciiDigit()

internal const val TRUE_VALUE: String = "true"

/** Whether [value] is a `-u-` keyword value UTS #35 would accept, case aside. */
internal fun isWellFormedUnicodeValue(value: String): Boolean =
    value == TRUE_VALUE || value.split('-').all { it.length in 3..8 && it.all(Char::isAsciiAlphanumeric) }

private class ParsedExtensions(val attributes: List<String>, val keywords: Map<String, String>, val other: Map<Char, String>)

private fun parseExtensions(parts: List<String>, from: Int): ParsedExtensions {
    val attributes = ArrayList<String>()
    val keywords = LinkedHashMap<String, String>()
    val other = LinkedHashMap<Char, String>()
    var index = from
    while (index < parts.size) {
        val singleton = parts[index][0].lowercaseChar()
        index++
        if (singleton == 'u') {
            var key: String? = null
            val values = ArrayList<String>()
            fun flush() {
                key?.let { keywords[it] = values.joinToString("-").ifEmpty { TRUE_VALUE } }
                values.clear()
            }
            while (index < parts.size && parts[index].length != 1) {
                val subtag = parts[index].lowercase()
                when {
                    subtag.length == 2 -> {
                        flush()
                        key = subtag
                    }
                    key == null -> attributes.add(subtag)
                    else -> values.add(subtag)
                }
                index++
            }
            flush()
        } else {
            val payload = ArrayList<String>()
            while (index < parts.size && (singleton == 'x' || parts[index].length != 1)) {
                payload.add(parts[index].lowercase())
                index++
            }
            if (payload.isNotEmpty()) other[singleton] = payload.joinToString("-")
        }
    }
    return ParsedExtensions(attributes, keywords, other)
}

private val OLD_KEYWORD_ALIASES = mapOf(
    "calendar" to "ca",
    "collation" to "co",
    "currency" to "cu",
    "hours" to "hc",
    "numbers" to "nu",
    "timezone" to "tz",
    "variant" to "va",
)

private fun parseOldKeywords(segment: String): Map<String, String> {
    if (segment.isEmpty()) return emptyMap()
    val keywords = LinkedHashMap<String, String>()
    for (entry in segment.split(';')) {
        val name = entry.substringBefore('=', "").lowercase()
        val value = entry.substringAfter('=', "").lowercase()
        if (name.isEmpty() || value.isEmpty() || !isWellFormedUnicodeValue(value)) continue
        val key = OLD_KEYWORD_ALIASES[name] ?: name
        if (key.length == 2) keywords[key] = value
    }
    return keywords
}

private fun ParsedExtensions.mergedWith(old: Map<String, String>): ParsedExtensions =
    if (old.isEmpty()) this else ParsedExtensions(attributes, keywords + old, other)
