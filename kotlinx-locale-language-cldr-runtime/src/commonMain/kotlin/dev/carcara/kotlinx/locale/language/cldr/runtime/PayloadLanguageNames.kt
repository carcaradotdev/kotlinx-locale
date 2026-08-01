@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.language.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.internal.ENTRY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.sparseRecordValue
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf
import dev.carcara.kotlinx.locale.language.LanguageNameSource
import dev.carcara.kotlinx.locale.language.LanguageNameStyle
import dev.carcara.kotlinx.locale.language.LocaleDisplayPatterns

/** The parent tag plus four keyed fields: languages, scripts, territories, patterns. */
private const val FIELD_COUNT = 5

/** The suffix a short-form entry is keyed under, so both spellings share one table. */
private const val SHORT_SUFFIX = "#short"

/**
 * A [LanguageNameSource] over a table of CLDR locale display name records.
 *
 * Each record is the parent tag, then only what this locale's own file declares:
 * language names, script names, territory names and the three composition
 * patterns. A lookup walks the parent chain for whatever the locale leaves out,
 * which is what keeps the table from repeating every English name under every
 * English variant.
 */
public class PayloadLanguageNames(private val records: Map<String, String>) : LanguageNameSource {

    override val supportedLocales: Set<Locale> by lazy { supportedLocalesOf(records) }

    override fun languageNameOrNull(subtag: String, style: LanguageNameStyle, locale: Locale): String? {
        val key = subtag.replace('-', '_')
        if (style == LanguageNameStyle.SHORT) {
            sparseRecordValue(records, locale, field = 1, fieldCount = FIELD_COUNT, key = key + SHORT_SUFFIX)
                ?.let { return it }
        }
        return sparseRecordValue(records, locale, field = 1, fieldCount = FIELD_COUNT, key = key)
    }

    override fun scriptNameOrNull(code: String, locale: Locale): String? =
        sparseRecordValue(records, locale, field = 2, fieldCount = FIELD_COUNT, key = code)

    override fun regionNameOrNull(code: String, locale: Locale): String? =
        sparseRecordValue(records, locale, field = 3, fieldCount = FIELD_COUNT, key = code)

    /**
     * The three patterns, walked up the chain together rather than one at a
     * time.
     *
     * Together, because a locale that overrides only its separator still means
     * its parent's bracket style, and reading each independently would be the
     * same answer for more work.
     */
    override fun displayPatternsOrNull(locale: Locale): LocaleDisplayPatterns? {
        val pattern = sparseRecordValue(records, locale, field = 4, fieldCount = FIELD_COUNT, key = "p")
            ?: return null
        val parts = pattern.split(ENTRY_SEPARATOR)
        return LocaleDisplayPatterns(
            localePattern = parts.getOrNull(0)?.takeIf(String::isNotEmpty) ?: LocaleDisplayPatterns.Root.localePattern,
            localeSeparator = parts.getOrNull(1)?.takeIf(String::isNotEmpty)
                ?: LocaleDisplayPatterns.Root.localeSeparator,
            localeKeyTypePattern = parts.getOrNull(2)?.takeIf(String::isNotEmpty)
                ?: LocaleDisplayPatterns.Root.localeKeyTypePattern,
        )
    }
}
