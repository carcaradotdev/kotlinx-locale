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
) {

    /** The canonical BCP 47 language tag, e.g. `pt-BR` or `sr-Cyrl-BA`. */
    public fun toLanguageTag(): String = buildString {
        append(language)
        script?.let { append('-').append(it) }
        region?.let { append('-').append(it) }
        variant?.let { append('-').append(it) }
    }

    override fun toString(): String = toLanguageTag()

    override fun equals(other: Any?): Boolean = other is Locale &&
        language == other.language &&
        script == other.script &&
        region == other.region &&
        variant == other.variant

    override fun hashCode(): Int {
        var result = language.hashCode()
        result = 31 * result + (script?.hashCode() ?: 0)
        result = 31 * result + (region?.hashCode() ?: 0)
        result = 31 * result + (variant?.hashCode() ?: 0)
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
                language = legacyLanguageAliases[language.lowercase()] ?: language.lowercase(),
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
            )
        }

        /**
         * Parses a language tag leniently: accepts BCP 47 (`pt-BR`) as well as
         * POSIX-style identifiers (`pt_BR.UTF-8@latin`). Unicode extensions and
         * anything after a singleton subtag (`-u-`, `-x-`) are ignored.
         *
         * Returns `null` when no valid language subtag can be extracted.
         */
        public fun forLanguageTagOrNull(tag: String): Locale? {
            val cleaned = tag.substringBefore('.').substringBefore('@').trim()
            if (cleaned.isEmpty()) return null
            val parts = cleaned.split('-', '_').filter(String::isNotEmpty)
            if (parts.isEmpty()) return null

            val language = parts[0].lowercase()
            if (language.length !in 2..8 || !language.all(Char::isLatinLetter)) return null
            if (language == "c" || language == "posix") return null

            var script: String? = null
            var region: String? = null
            var variant: String? = null
            for (index in 1 until parts.size) {
                val part = parts[index]
                if (part.length == 1) break // singleton starts extensions: ignore the rest
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
            }
            return of(language, script, region, variant)
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

private val legacyLanguageAliases: Map<String, String> = mapOf(
    "iw" to "he",
    "in" to "id",
    "ji" to "yi",
    "mo" to "ro",
    "tl" to "fil",
)

private fun Char.isLatinLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
