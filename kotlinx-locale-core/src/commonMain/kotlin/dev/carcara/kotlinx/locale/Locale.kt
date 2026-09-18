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
 * An immutable Unicode locale identifier: language, optional script, region and variant.
 *
 * Instances are normalized: lowercase language and variant, title-case script,
 * uppercase region. Create instances with [of] or [forLanguageTag].
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
         * Parses a language tag leniently: accepts BCP 47 (`pt-BR`) as well as
         * POSIX-style identifiers (`pt_BR.UTF-8@latin`). Unicode locale extensions
         * (`-u-`) and other singleton extensions (`-x-`, `-t-`, ...) are kept and
         * canonicalised: lowercased, with their subtags and singletons reordered.
         *
         * Returns `null` when no valid language subtag can be extracted.
         */
        public fun forLanguageTagOrNull(tag: String): Locale? {
            val cleaned = tag.substringBefore('@').substringBefore('.').trim()
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

            val extensions = parseExtensions(parts, index)
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
         * The current locale of the underlying platform, or `en` when the platform
         * does not expose one (e.g. WASI).
         */
        public val current: Locale
            get() = platformSystemLocaleTag()?.let(::forLanguageTagOrNull) ?: of("en")
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

internal const val TRUE_VALUE: String = "true"

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
            while (index < parts.size && (parts[index].length != 1 || (singleton == 'x' && payload.isEmpty()))) {
                payload.add(parts[index].lowercase())
                index++
            }
            if (payload.isNotEmpty()) other[singleton] = payload.joinToString("-")
        }
    }
    return ParsedExtensions(attributes, keywords, other)
}
